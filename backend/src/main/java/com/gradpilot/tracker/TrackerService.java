package com.gradpilot.tracker;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.tracker.dto.TrackerDtos.CreateTrackerRequest;
import com.gradpilot.tracker.dto.TrackerDtos.ItemRequest;
import com.gradpilot.tracker.dto.TrackerDtos.ItemResponse;
import com.gradpilot.tracker.dto.TrackerDtos.TrackerDetailResponse;
import com.gradpilot.tracker.dto.TrackerDtos.TrackerResponse;
import com.gradpilot.tracker.dto.TrackerDtos.TrackerStats;
import com.gradpilot.tracker.dto.TrackerDtos.UpdateTrackerRequest;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;
import com.gradpilot.web.NotFoundException;

@Service
public class TrackerService {

    private final TrackerRepository trackers;
    private final ItemRepository items;
    private final UserRepository users;
    private final TemplateCatalog templates;
    private final Clock clock;

    public TrackerService(TrackerRepository trackers, ItemRepository items, UserRepository users,
                          TemplateCatalog templates, Clock clock) {
        this.trackers = trackers;
        this.items = items;
        this.users = users;
        this.templates = templates;
        this.clock = clock;
    }

    // ---- Trackers -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TrackerResponse> list(UUID userId) {
        List<Tracker> owned = trackers.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        Map<UUID, List<Item>> itemsByTracker = items
                .findByTrackerIdIn(owned.stream().map(Tracker::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Item::getTrackerId));
        LocalDate today = today(userId);
        return owned.stream()
                .map(t -> TrackerResponse.from(t, stats(t, itemsByTracker.getOrDefault(t.getId(), List.of()), today)))
                .toList();
    }

    @Transactional
    public TrackerDetailResponse create(UUID userId, CreateTrackerRequest request) {
        Tracker tracker;
        List<String> starterItems = List.of();

        if (hasText(request.template())) {
            TrackerTemplate template = templates.find(request.template())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + request.template()));
            String name = hasText(request.name()) ? request.name().trim() : template.name();
            tracker = new Tracker(userId, name, template.type());
            tracker.setTemplate(template.key());
            Map<String, Object> settings = new LinkedHashMap<>(template.settings());
            if (request.settings() != null) {
                settings.putAll(request.settings());
            }
            tracker.setSettings(settings);
            starterItems = template.starterItems();
        } else {
            if (!hasText(request.name()) || request.type() == null) {
                throw new IllegalArgumentException("Give a template, or a name and a type");
            }
            tracker = new Tracker(userId, request.name().trim(), request.type());
            tracker.setSettings(request.settings());
        }

        validateSettings(tracker);
        tracker.setColour(request.colour());
        tracker.setSortOrder((int) trackers.countByUserId(userId));
        tracker = trackers.save(tracker);

        List<Item> created = new ArrayList<>();
        for (int i = 0; i < starterItems.size(); i++) {
            Item item = new Item(tracker.getId(), starterItems.get(i));
            item.setSortOrder(i);
            created.add(item);
        }
        items.saveAll(created);

        return detail(tracker, created, today(userId));
    }

    @Transactional(readOnly = true)
    public TrackerDetailResponse get(UUID userId, UUID trackerId) {
        Tracker tracker = owned(userId, trackerId);
        return detail(tracker, items.findByTrackerIdOrderBySortOrderAscCreatedAtAsc(trackerId), today(userId));
    }

    @Transactional
    public TrackerDetailResponse update(UUID userId, UUID trackerId, UpdateTrackerRequest request) {
        Tracker tracker = owned(userId, trackerId);
        tracker.setName(request.name().trim());
        tracker.setColour(request.colour());
        if (request.settings() != null) {
            tracker.setSettings(request.settings());
            validateSettings(tracker);
        }
        if (request.sortOrder() != null) {
            tracker.setSortOrder(request.sortOrder());
        }
        return detail(tracker, items.findByTrackerIdOrderBySortOrderAscCreatedAtAsc(trackerId), today(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID trackerId) {
        trackers.delete(owned(userId, trackerId));
    }

    // ---- Items ----------------------------------------------------------

    @Transactional
    public ItemResponse addItem(UUID userId, UUID trackerId, ItemRequest request) {
        Tracker tracker = owned(userId, trackerId);
        Item item = new Item(tracker.getId(), request.title().trim());
        if (request.sortOrder() == null) {
            item.setSortOrder((int) items.countByTrackerId(trackerId));
        }
        apply(tracker, item, request, userId);
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public ItemResponse updateItem(UUID userId, UUID trackerId, UUID itemId, ItemRequest request) {
        Tracker tracker = owned(userId, trackerId);
        Item item = items.findByIdAndTrackerId(itemId, trackerId)
                .orElseThrow(() -> new NotFoundException("Item"));
        item.setTitle(request.title().trim());
        apply(tracker, item, request, userId);
        return ItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(UUID userId, UUID trackerId, UUID itemId) {
        owned(userId, trackerId);
        Item item = items.findByIdAndTrackerId(itemId, trackerId)
                .orElseThrow(() -> new NotFoundException("Item"));
        items.delete(item);
    }

    // ---- Helpers --------------------------------------------------------

    Tracker owned(UUID userId, UUID trackerId) {
        return trackers.findByIdAndUserId(trackerId, userId)
                .orElseThrow(() -> new NotFoundException("Tracker"));
    }

    private void apply(Tracker tracker, Item item, ItemRequest request, UUID userId) {
        item.setNotes(request.notes());
        item.setDone(Boolean.TRUE.equals(request.done()));
        item.setProgress(request.progress());
        item.setAmount(request.amount());
        item.setItemDate(request.itemDate());
        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());
        item.setFields(request.fields());
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        }

        switch (tracker.getType()) {
            case PIPELINE -> item.setStage(pipelineStage(tracker, request.stage()));
            case SCHEDULE -> {
                if (request.itemDate() == null || request.startTime() == null) {
                    throw new IllegalArgumentException("A shift needs a date and a start time");
                }
                item.setStage(request.stage());
            }
            case COUNTDOWN -> {
                if (request.itemDate() == null) {
                    throw new IllegalArgumentException("A countdown needs a date");
                }
                item.setStage(request.stage());
            }
            default -> item.setStage(request.stage());
        }

        Instant reminder = request.reminderAt();
        if (reminder == null && tracker.getType() == TrackerType.SCHEDULE) {
            reminder = leaveReminder(tracker, item, zone(userId));
        }
        item.setReminderAt(reminder);
        Instant now = clock.instant();
        if (reminder != null && reminder.isBefore(now) && item.getReminderSentAt() == null) {
            // Never fire reminders for moments that have already passed
            item.markReminderSent(now);
        }
    }

    /** For a shift, remind when it is time to leave home. */
    static Instant leaveReminder(Tracker tracker, Item shift, ZoneId zone) {
        return shift.getItemDate()
                .atTime(shift.getStartTime())
                .atZone(zone)
                .minusMinutes(tracker.travelMinutes())
                .toInstant();
    }

    private static String pipelineStage(Tracker tracker, String requested) {
        List<String> stages = tracker.stages();
        if (!hasText(requested)) {
            return stages.isEmpty() ? null : stages.get(0);
        }
        if (!stages.isEmpty() && !stages.contains(requested)) {
            throw new IllegalArgumentException("Stage must be one of " + stages);
        }
        return requested;
    }

    private static void validateSettings(Tracker tracker) {
        Object stages = tracker.getSettings().get(Tracker.SETTING_STAGES);
        if (stages != null && !(stages instanceof List<?>)) {
            throw new IllegalArgumentException("settings.stages must be a list");
        }
        Object username = tracker.getSettings().get("leetcodeUsername");
        if (username != null && !(username instanceof String name && name.matches("[A-Za-z0-9_-]{0,40}"))) {
            throw new IllegalArgumentException("settings.leetcodeUsername can only use letters, numbers, - and _");
        }
        Object travel = tracker.getSettings().get(Tracker.SETTING_TRAVEL_MINUTES);
        if (travel != null && !(travel instanceof Number number && number.intValue() >= 0
                && number.intValue() <= 600)) {
            throw new IllegalArgumentException("settings.travelMinutes must be between 0 and 600");
        }
    }

    private TrackerDetailResponse detail(Tracker tracker, List<Item> trackerItems, LocalDate today) {
        List<Item> sorted = trackerItems.stream()
                .sorted(Comparator.comparingInt(Item::getSortOrder))
                .toList();
        return new TrackerDetailResponse(
                TrackerResponse.from(tracker, stats(tracker, sorted, today)),
                sorted.stream().map(ItemResponse::from).toList());
    }

    static TrackerStats stats(Tracker tracker, List<Item> trackerItems, LocalDate today) {
        int total = trackerItems.size();
        int done = (int) trackerItems.stream().filter(Item::isDone).count();

        Integer averageProgress = null;
        if (tracker.getType() == TrackerType.PROGRESS) {
            OptionalDouble average = trackerItems.stream()
                    .map(Item::getProgress).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).average();
            averageProgress = average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
        }

        BigDecimal totalAmount = null;
        if (tracker.getType() == TrackerType.LOG) {
            totalAmount = trackerItems.stream()
                    .map(Item::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<String, Integer> byStage = null;
        if (tracker.getType() == TrackerType.PIPELINE) {
            byStage = new LinkedHashMap<>();
            for (String stage : tracker.stages()) {
                byStage.put(stage, 0);
            }
            for (Item item : trackerItems) {
                if (item.getStage() != null) {
                    byStage.merge(item.getStage(), 1, Integer::sum);
                }
            }
        }

        LocalDate nextDate = trackerItems.stream()
                .map(Item::getItemDate)
                .filter(d -> d != null && !d.isBefore(today))
                .min(LocalDate::compareTo)
                .orElse(null);

        return new TrackerStats(total, done, averageProgress, totalAmount, byStage, nextDate);
    }

    private LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(zone(userId)));
    }

    private ZoneId zone(UUID userId) {
        return users.findById(userId)
                .map(User::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneId.of(User.DEFAULT_TIMEZONE));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
