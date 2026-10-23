package com.gradpilot.jobs;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.gradpilot.tracker.TemplateCatalog;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerRepository;
import com.gradpilot.tracker.TrackerType;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;

/**
 * Turns job emails into Job hunt items: a confirmation adds an application,
 * and later emails about it move it to Assessment, Interview, Offer or
 * Rejected. The same email is never counted twice.
 */
@Service
public class JobImportService {

    static final String JOB_TEMPLATE = "job-hunt";
    static final String FIELD_COMPANY = "company";
    static final String FIELD_ROLE = "role";
    static final String FIELD_SOURCE = "source";
    static final String FIELD_EMAILS = "emailIds";
    private static final int MAX_EMAIL_IDS = 30;
    private static final int MAX_NOTES = 5000;
    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public enum Outcome {
        /** A new application was added. */
        CREATED,
        /** An application moved to a new stage. */
        UPDATED,
        /** The email was about an application that is already at this stage or later. */
        UNCHANGED,
        /** This exact email was imported before. */
        DUPLICATE,
        /** The email is about a job, but names neither the company nor the role. */
        NOT_ENOUGH_DETAIL,
        /** The email is not about a job application. */
        NOT_A_JOB_EMAIL
    }

    public record Result(
            Outcome outcome,
            JobEmailKind kind,
            String company,
            String role,
            String source,
            UUID trackerId,
            UUID itemId,
            String title,
            String stage,
            String previousStage) {

        static Result of(Outcome outcome) {
            return new Result(outcome, null, null, null, null, null, null, null, null, null);
        }
    }

    private final JobEmailParser parser;
    private final TrackerRepository trackers;
    private final ItemRepository items;
    private final UserRepository users;
    private final Clock clock;

    public JobImportService(JobEmailParser parser, TrackerRepository trackers, ItemRepository items,
                            UserRepository users, Clock clock) {
        this.parser = parser;
        this.trackers = trackers;
        this.items = items;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public Result importEmail(UUID userId, JobEmail email) {
        Optional<JobEmailFinding> parsed = parser.parse(email);
        if (parsed.isEmpty()) {
            return Result.of(Outcome.NOT_A_JOB_EMAIL);
        }
        JobEmailFinding finding = parsed.get();
        if (finding.company() == null && finding.role() == null) {
            return new Result(Outcome.NOT_ENOUGH_DETAIL, finding.kind(), null, null, finding.source(),
                    null, null, null, null, null);
        }

        Tracker tracker = jobTracker(userId);
        List<Item> existing = items.findByTrackerIdOrderBySortOrderAscCreatedAtAsc(tracker.getId());

        for (Item item : existing) {
            if (emailIds(item).contains(email.messageId())) {
                return result(Outcome.DUPLICATE, finding, tracker, item, item.getStage());
            }
        }

        Instant received = email.receivedAt() != null ? email.receivedAt() : clock.instant();
        LocalDate day = received.atZone(zone(userId)).toLocalDate();
        String targetStage = stageFor(tracker, finding.kind());
        Optional<Item> match = findApplication(existing, finding);

        if (match.isEmpty()) {
            Item item = new Item(tracker.getId(), title(finding));
            item.setStage(targetStage);
            item.setItemDate(day);
            item.setSortOrder(existing.size());
            Map<String, Object> fields = new LinkedHashMap<>();
            putIfPresent(fields, FIELD_COMPANY, finding.company());
            putIfPresent(fields, FIELD_ROLE, finding.role());
            putIfPresent(fields, FIELD_SOURCE, finding.source());
            fields.put(FIELD_EMAILS, List.of(email.messageId()));
            item.setFields(fields);
            item.setNotes(noteLine(day, finding.kind(), email.subject()));
            items.save(item);
            return result(Outcome.CREATED, finding, tracker, item, null);
        }

        Item item = match.get();
        String previous = item.getStage();
        Map<String, Object> fields = new LinkedHashMap<>(item.getFields());
        List<String> ids = new ArrayList<>(emailIds(item));
        ids.add(email.messageId());
        fields.put(FIELD_EMAILS, ids.subList(Math.max(0, ids.size() - MAX_EMAIL_IDS), ids.size()));
        fields.putIfAbsent(FIELD_COMPANY, finding.company());
        if (finding.role() != null) {
            fields.putIfAbsent(FIELD_ROLE, finding.role());
        }
        if (finding.source() != null && !"Email".equals(finding.source())) {
            fields.putIfAbsent(FIELD_SOURCE, finding.source());
        }
        fields.values().removeIf(Objects::isNull);
        item.setFields(fields);
        item.setNotes(appendNote(item.getNotes(), noteLine(day, finding.kind(), email.subject())));

        if (shouldMove(tracker, previous, targetStage, finding.kind())) {
            item.setStage(targetStage);
            return result(Outcome.UPDATED, finding, tracker, item, previous);
        }
        return result(Outcome.UNCHANGED, finding, tracker, item, previous);
    }

    /** The person's Job hunt tracker, created the first time it is needed. */
    Tracker jobTracker(UUID userId) {
        List<Tracker> owned = trackers.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        return owned.stream()
                .filter(t -> JOB_TEMPLATE.equals(t.getTemplate()))
                .findFirst()
                .or(() -> owned.stream()
                        .filter(t -> t.getType() == TrackerType.PIPELINE && t.stages().contains("Applied"))
                        .findFirst())
                .orElseGet(() -> {
                    Tracker tracker = new Tracker(userId, "Job hunt", TrackerType.PIPELINE);
                    tracker.setTemplate(JOB_TEMPLATE);
                    tracker.setSettings(Map.of(Tracker.SETTING_STAGES, TemplateCatalog.JOB_STAGES));
                    tracker.setSortOrder(owned.size());
                    return trackers.save(tracker);
                });
    }

    /**
     * The application an email is about: same company, and the same role
     * when both name one. People often apply for several roles at one company.
     */
    static Optional<Item> findApplication(List<Item> existing, JobEmailFinding finding) {
        String company = key(finding.company());
        String role = key(finding.role());
        return existing.stream()
                .filter(item -> {
                    String itemCompany = key(text(item.getFields().get(FIELD_COMPANY)));
                    String itemRole = key(text(item.getFields().get(FIELD_ROLE)));
                    String itemTitle = key(item.getTitle());
                    boolean sameCompany = company != null
                            && (company.equals(itemCompany) || (itemCompany == null && itemTitle != null
                            && itemTitle.startsWith(company)));
                    if (company != null && !sameCompany) {
                        return false;
                    }
                    if (role != null && itemRole != null) {
                        return role.equals(itemRole);
                    }
                    if (company == null) {
                        return role != null && (role.equals(itemRole) || (itemTitle != null && itemTitle.contains(role)));
                    }
                    return true;
                })
                .max(Comparator.comparing(Item::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** Move forward through the stages, but always record an offer or a rejection. */
    static boolean shouldMove(Tracker tracker, String current, String target, JobEmailKind kind) {
        if (target == null || target.equals(current)) {
            return false;
        }
        if (kind == JobEmailKind.REJECTED || kind == JobEmailKind.OFFER) {
            return true;
        }
        List<String> stages = tracker.stages();
        int from = current == null ? -1 : stages.indexOf(current);
        int to = stages.indexOf(target);
        boolean finished = "Offer".equals(current) || "Rejected".equals(current);
        return !finished && to > from;
    }

    private static String stageFor(Tracker tracker, JobEmailKind kind) {
        List<String> stages = tracker.stages();
        if (stages.contains(kind.stage())) {
            return kind.stage();
        }
        return stages.isEmpty() ? null : stages.get(0);
    }

    static String title(JobEmailFinding finding) {
        String title;
        if (finding.company() != null && finding.role() != null) {
            title = finding.company() + " – " + finding.role();
        } else {
            title = finding.company() != null ? finding.company() : finding.role();
        }
        return title.length() > 200 ? title.substring(0, 200) : title;
    }

    private static String noteLine(LocalDate day, JobEmailKind kind, String subject) {
        String what = switch (kind) {
            case APPLIED -> "Application confirmed";
            case ASSESSMENT -> "Assessment invitation";
            case INTERVIEW -> "Interview invitation";
            case OFFER -> "Offer";
            case REJECTED -> "Unsuccessful";
        };
        String line = NOTE_DATE.format(day) + ": " + what;
        if (subject != null && !subject.isBlank()) {
            String shortSubject = subject.length() > 120 ? subject.substring(0, 120) + "…" : subject;
            line += " (" + shortSubject.trim() + ")";
        }
        return line;
    }

    private static String appendNote(String notes, String line) {
        String combined = notes == null || notes.isBlank() ? line : notes + "\n" + line;
        return combined.length() > MAX_NOTES ? combined.substring(combined.length() - MAX_NOTES) : combined;
    }

    @SuppressWarnings("unchecked")
    private static List<String> emailIds(Item item) {
        Object value = item.getFields().get(FIELD_EMAILS);
        if (value instanceof List<?> list) {
            return ((List<Object>) list).stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** Lower case, letters and digits only, without "Pty Ltd" and similar endings. */
    static String key(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String key = value.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(?:pty|ltd|limited|inc|incorporated|llc|plc|group|australia|au|co)\\b", " ")
                .replaceAll("[^a-z0-9]+", "");
        return key.isEmpty() ? null : key;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static void putIfPresent(Map<String, Object> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private Result result(Outcome outcome, JobEmailFinding finding, Tracker tracker, Item item, String previous) {
        return new Result(outcome, finding.kind(), finding.company(), finding.role(), finding.source(),
                tracker.getId(), item.getId(), item.getTitle(), item.getStage(), previous);
    }

    private ZoneId zone(UUID userId) {
        return users.findById(userId)
                .map(User::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneId.of(User.DEFAULT_TIMEZONE));
    }
}
