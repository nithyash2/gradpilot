package com.gradpilot.notify;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.push.PushMessage;
import com.gradpilot.push.PushService;
import com.gradpilot.push.PushSubscriptionRepository;
import com.gradpilot.review.WeekReviewService;
import com.gradpilot.today.TodayDtos.TodayResponse;
import com.gradpilot.today.TodayService;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerRepository;
import com.gradpilot.tracker.TrackerType;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;

/** Decides which notifications are due right now and sends them. */
@Service
public class NotificationService {

    /** A missed morning plan is still sent up to two hours late, never later. */
    static final long WINDOW_MINUTES = 120;
    static final int MAX_REMINDERS_PER_RUN = 200;

    private final PushService push;
    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final TodayService today;
    private final ItemRepository items;
    private final TrackerRepository trackers;
    private final WeekReviewService reviews;
    private final Clock clock;

    public NotificationService(PushService push, PushSubscriptionRepository subscriptions, UserRepository users,
                               TodayService today, ItemRepository items, TrackerRepository trackers,
                               WeekReviewService reviews, Clock clock) {
        this.push = push;
        this.subscriptions = subscriptions;
        this.users = users;
        this.today = today;
        this.items = items;
        this.trackers = trackers;
        this.reviews = reviews;
        this.clock = clock;
    }

    /** Morning plans and evening wind-downs whose time has come. Returns how many were sent. */
    @Transactional
    public int sendDailyDigests() {
        if (!push.enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        int sent = 0;
        for (UUID userId : subscriptions.findSubscribedUserIds()) {
            User user = users.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }
            ZonedDateTime local = now.atZone(ZoneId.of(user.getTimezone()));
            LocalDate date = local.toLocalDate();
            LocalTime time = local.toLocalTime();

            if (isDue(time, user.getMorningTime(), user.getLastMorningSent(), date)) {
                TodayResponse view = today.today(userId);
                push.sendToUser(userId, DigestMessages.morning(user, view));
                user.setLastMorningSent(date);
                sent++;
            }
            if (isDue(time, user.getEveningTime(), user.getLastEveningSent(), date)) {
                if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    // Sunday evening looks back at the whole week instead
                    push.sendToUser(userId, WeekReviewService.message(reviews.week(userId, date)));
                } else {
                    TodayResponse view = today.today(userId);
                    push.sendToUser(userId, DigestMessages.evening(user, view));
                }
                user.setLastEveningSent(date);
                sent++;
            }
        }
        return sent;
    }

    /** Item reminders, such as "time to leave for work". Returns how many were processed. */
    @Transactional
    public int sendDueReminders() {
        if (!push.enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        List<Item> due = items.findDueReminders(now).stream().limit(MAX_REMINDERS_PER_RUN).toList();
        for (Item item : due) {
            trackers.findById(item.getTrackerId()).ifPresent(tracker ->
                    push.sendToUser(tracker.getUserId(), reminderMessage(tracker, item)));
            // Marked as sent even if no device accepted it, so it never repeats
            item.markReminderSent(now);
        }
        return due.size();
    }

    static PushMessage reminderMessage(Tracker tracker, Item item) {
        String url = "/trackers/" + tracker.getId();
        if (tracker.getType() == TrackerType.SCHEDULE) {
            String starts = item.getStartTime() == null ? "" : " starts at " + DigestMessages.time(item.getStartTime());
            return new PushMessage("Time to leave", item.getTitle() + starts, url, "leave-" + item.getId(), true);
        }
        return PushMessage.of("Reminder: " + item.getTitle(), tracker.getName(), url, "item-" + item.getId());
    }

    /** True when the target time has passed today, within the window, and nothing was sent today yet. */
    static boolean isDue(LocalTime now, LocalTime target, LocalDate lastSent, LocalDate today) {
        if (target == null || today.equals(lastSent) || now.isBefore(target)) {
            return false;
        }
        return ChronoUnit.MINUTES.between(target, now) < WINDOW_MINUTES;
    }
}
