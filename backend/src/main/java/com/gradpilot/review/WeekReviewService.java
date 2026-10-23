package com.gradpilot.review;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.checkin.CheckIn;
import com.gradpilot.checkin.CheckInRepository;
import com.gradpilot.push.PushMessage;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerRepository;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;

/**
 * A look back at the last seven days: the daily one thing, job applications,
 * LeetCode, shifts and anything else logged. Sent as a notification on
 * Sunday evenings and shown in the app any time.
 */
@Service
public class WeekReviewService {

    public record Day(LocalDate date, boolean checkedIn, String focus, boolean focusDone) {
    }

    public record Jobs(int applied, int assessments, int interviews, int offers, int rejections, int active) {
    }

    public record Shifts(int count, double hours) {
    }

    public record Logged(UUID trackerId, String name, BigDecimal amount, String unit) {
    }

    public record Study(UUID trackerId, String name, Integer averageProgress) {
    }

    public record WeekReview(
            LocalDate start,
            LocalDate end,
            List<Day> days,
            int focusDays,
            int streak,
            Jobs jobs,
            int leetcodeSolved,
            Shifts shifts,
            List<Logged> logs,
            List<Study> study) {
    }

    private final CheckInRepository checkins;
    private final TrackerRepository trackers;
    private final ItemRepository items;
    private final UserRepository users;
    private final Clock clock;

    public WeekReviewService(CheckInRepository checkins, TrackerRepository trackers, ItemRepository items,
                             UserRepository users, Clock clock) {
        this.checkins = checkins;
        this.trackers = trackers;
        this.items = items;
        this.users = users;
        this.clock = clock;
    }

    /** The seven days ending on {@code end}, or ending today when it is null. */
    @Transactional(readOnly = true)
    public WeekReview week(UUID userId, LocalDate end) {
        ZoneId zone = zone(userId);
        LocalDate last = end != null ? end : LocalDate.now(clock.withZone(zone));
        LocalDate first = last.minusDays(6);
        Instant from = first.atStartOfDay(zone).toInstant();
        Instant to = last.plusDays(1).atStartOfDay(zone).toInstant();

        // Days, and the streak of days with the one thing done
        Map<LocalDate, CheckIn> byDay = checkins
                .findByUserIdAndDayBetweenOrderByDayAsc(userId, last.minusDays(60), last)
                .stream().collect(Collectors.toMap(CheckIn::getDay, Function.identity()));
        List<Day> days = new ArrayList<>();
        int focusDays = 0;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            CheckIn c = byDay.get(d);
            boolean done = c != null && c.isFocusDone();
            days.add(new Day(d, c != null, c == null ? null : c.getFocus(), done));
            if (done) {
                focusDays++;
            }
        }
        int streak = streak(byDay, last);

        List<Tracker> owned = trackers.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        Map<UUID, List<Item>> itemsByTracker = items
                .findByTrackerIdIn(owned.stream().map(Tracker::getId).toList())
                .stream().collect(Collectors.groupingBy(Item::getTrackerId));

        int applied = 0;
        int assessments = 0;
        int interviews = 0;
        int offers = 0;
        int rejections = 0;
        int active = 0;
        int leetcode = 0;
        int shiftCount = 0;
        long shiftMinutes = 0;
        List<Logged> logs = new ArrayList<>();
        List<Study> study = new ArrayList<>();

        for (Tracker tracker : owned) {
            List<Item> list = itemsByTracker.getOrDefault(tracker.getId(), List.of());
            switch (tracker.getType()) {
                case PIPELINE -> {
                    if (!tracker.stages().contains("Applied")) {
                        break;
                    }
                    for (Item item : list) {
                        String stage = item.getStage();
                        boolean changedThisWeek = inRange(item.getUpdatedAt(), from, to);
                        if (between(item.getItemDate(), first, last) && !"To apply".equals(stage)) {
                            applied++;
                        }
                        if (changedThisWeek && "Assessment".equals(stage)) {
                            assessments++;
                        } else if (changedThisWeek && "Interview".equals(stage)) {
                            interviews++;
                        } else if (changedThisWeek && "Offer".equals(stage)) {
                            offers++;
                        } else if (changedThisWeek && "Rejected".equals(stage)) {
                            rejections++;
                        }
                        if (stage != null && !List.of("To apply", "Offer", "Rejected").contains(stage)) {
                            active++;
                        }
                    }
                }
                case LOG -> {
                    BigDecimal sum = list.stream()
                            .filter(i -> between(i.getItemDate(), first, last))
                            .map(Item::getAmount).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if ("leetcode".equals(tracker.getTemplate())) {
                        leetcode += sum.intValue();
                    } else if (sum.signum() > 0) {
                        Object unit = tracker.getSettings().get("unit");
                        logs.add(new Logged(tracker.getId(), tracker.getName(), sum,
                                unit == null ? null : unit.toString()));
                    }
                }
                case SCHEDULE -> {
                    for (Item item : list) {
                        if (between(item.getItemDate(), first, last) && item.getStartTime() != null) {
                            shiftCount++;
                            shiftMinutes += minutes(item.getStartTime(), item.getEndTime());
                        }
                    }
                }
                case PROGRESS -> {
                    List<Integer> values = list.stream().map(Item::getProgress).filter(Objects::nonNull).toList();
                    Integer average = values.isEmpty() ? null
                            : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0));
                    study.add(new Study(tracker.getId(), tracker.getName(), average));
                }
                default -> {
                    // Checklists and countdowns have no weekly totals
                }
            }
        }

        return new WeekReview(first, last, days, focusDays, streak,
                new Jobs(applied, assessments, interviews, offers, rejections, active),
                leetcode, new Shifts(shiftCount, Math.round(shiftMinutes / 6.0) / 10.0), logs, study);
    }

    /** The Sunday evening notification. */
    public static PushMessage message(WeekReview review) {
        List<String> parts = new ArrayList<>();
        Jobs jobs = review.jobs();
        if (jobs.applied() > 0) {
            parts.add(jobs.applied() + (jobs.applied() == 1 ? " application" : " applications"));
        }
        if (jobs.interviews() > 0) {
            parts.add(jobs.interviews() + (jobs.interviews() == 1 ? " interview" : " interviews"));
        }
        if (jobs.offers() > 0) {
            parts.add(jobs.offers() + (jobs.offers() == 1 ? " offer!" : " offers!"));
        }
        if (review.leetcodeSolved() > 0) {
            parts.add(review.leetcodeSolved() + " LeetCode");
        }
        if (review.shifts().count() > 0) {
            parts.add(review.shifts().count() + (review.shifts().count() == 1 ? " shift" : " shifts"));
        }
        parts.add(review.focusDays() + "/7 days with your one thing done");
        return PushMessage.of("Your week", String.join(" · ", parts), "/review", "weekly-review");
    }

    /** Days in a row with the one thing done, counting back from today (or yesterday if today is not done yet). */
    static int streak(Map<LocalDate, CheckIn> byDay, LocalDate today) {
        LocalDate day = today;
        CheckIn todays = byDay.get(today);
        if (todays == null || !todays.isFocusDone()) {
            day = today.minusDays(1);
        }
        int streak = 0;
        while (true) {
            CheckIn c = byDay.get(day);
            if (c == null || !c.isFocusDone()) {
                return streak;
            }
            streak++;
            day = day.minusDays(1);
        }
    }

    static long minutes(LocalTime start, LocalTime end) {
        if (end == null) {
            return 0;
        }
        long minutes = Duration.between(start, end).toMinutes();
        return minutes > 0 ? minutes : minutes + 24 * 60;
    }

    private static boolean between(LocalDate date, LocalDate first, LocalDate last) {
        return date != null && !date.isBefore(first) && !date.isAfter(last);
    }

    private static boolean inRange(Instant instant, Instant from, Instant to) {
        return instant != null && !instant.isBefore(from) && instant.isBefore(to);
    }

    private ZoneId zone(UUID userId) {
        return users.findById(userId)
                .map(User::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneId.of(User.DEFAULT_TIMEZONE));
    }
}
