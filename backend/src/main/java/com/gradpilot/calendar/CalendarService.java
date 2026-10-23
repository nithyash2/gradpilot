package com.gradpilot.calendar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.security.TokenCipher;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerRepository;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;

/**
 * A private calendar address per person. Calendar apps subscribe to it and
 * see shifts (with a "time to leave" alert), countdown dates and dated tasks.
 */
@Service
public class CalendarService {

    /** How far back and ahead the feed reaches. */
    private static final int DAYS_BACK = 60;
    private static final int DAYS_AHEAD = 400;

    private final CalendarFeedRepository feeds;
    private final TrackerRepository trackers;
    private final ItemRepository items;
    private final UserRepository users;
    private final TokenCipher cipher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CalendarService(CalendarFeedRepository feeds, TrackerRepository trackers, ItemRepository items,
                           UserRepository users, TokenCipher cipher, Clock clock) {
        this.feeds = feeds;
        this.trackers = trackers;
        this.items = items;
        this.users = users;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** The person's current token, if they turned the feed on. */
    @Transactional(readOnly = true)
    public Optional<String> token(UUID userId) {
        return feeds.findById(userId).map(feed -> cipher.decrypt(feed.getToken()));
    }

    /** Turns the feed on, or replaces the address so the old one stops working. */
    @Transactional
    public String newToken(UUID userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CalendarFeed feed = feeds.findById(userId).orElseGet(() -> new CalendarFeed(userId));
        feed.setTokenHash(hash(token));
        feed.setToken(cipher.encrypt(token));
        feed.setCreatedAt(clock.instant());
        feeds.save(feed);
        return token;
    }

    @Transactional
    public void turnOff(UUID userId) {
        feeds.findById(userId).ifPresent(feeds::delete);
    }

    /** The feed for a token, or empty when the token is unknown. */
    @Transactional(readOnly = true)
    public Optional<String> feed(String token) {
        if (token == null || token.length() < 20 || token.length() > 64) {
            return Optional.empty();
        }
        return feeds.findByTokenHash(hash(token)).map(feed -> render(feed.getUserId()));
    }

    String render(UUID userId) {
        ZoneId zone = users.findById(userId)
                .map(User::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneId.of(User.DEFAULT_TIMEZONE));
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = today.minusDays(DAYS_BACK);
        LocalDate to = today.plusDays(DAYS_AHEAD);

        List<Tracker> owned = trackers.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        Map<UUID, Tracker> byId = owned.stream().collect(Collectors.toMap(Tracker::getId, Function.identity()));
        List<IcsWriter.Event> events = new ArrayList<>();
        for (Item item : items.findByTrackerIdIn(byId.keySet())) {
            Tracker tracker = byId.get(item.getTrackerId());
            LocalDate day = item.getItemDate();
            if (tracker == null || day == null || day.isBefore(from) || day.isAfter(to)) {
                continue;
            }
            event(tracker, item, zone).ifPresent(events::add);
        }
        events.sort((a, b) -> start(a, zone).compareTo(start(b, zone)));
        return IcsWriter.write("GradPilot", events);
    }

    /** Shifts become timed events, countdowns and dated tasks become all-day events. */
    static Optional<IcsWriter.Event> event(Tracker tracker, Item item, ZoneId zone) {
        String uid = item.getId() + "@gradpilot";
        Instant stamp = item.getUpdatedAt() != null ? item.getUpdatedAt() : Instant.EPOCH;
        switch (tracker.getType()) {
            case SCHEDULE -> {
                if (item.getStartTime() == null) {
                    return Optional.empty();
                }
                ZonedDateTime start = item.getItemDate().atTime(item.getStartTime()).atZone(zone);
                ZonedDateTime end = item.getEndTime() == null
                        ? start.plusHours(1)
                        : item.getItemDate().atTime(item.getEndTime()).atZone(zone);
                if (!end.isAfter(start)) {
                    // A shift that finishes after midnight
                    end = end.plusDays(1);
                }
                int travel = tracker.travelMinutes();
                return Optional.of(IcsWriter.Event.timed(uid, item.getTitle(), tracker.getName(), stamp,
                        start.toInstant(), end.toInstant(), travel > 0 ? travel : null,
                        "Time to leave for " + item.getTitle()));
            }
            case COUNTDOWN -> {
                return Optional.of(IcsWriter.Event.allDay(uid, item.getTitle(), tracker.getName(), stamp,
                        item.getItemDate()));
            }
            case CHECKLIST, PROGRESS -> {
                if (item.isDone()) {
                    return Optional.empty();
                }
                return Optional.of(IcsWriter.Event.allDay(uid, item.getTitle(), tracker.getName(), stamp,
                        item.getItemDate()));
            }
            default -> {
                // Job applications and log entries are records of the past, not plans
                return Optional.empty();
            }
        }
    }

    private static Instant start(IcsWriter.Event event, ZoneId zone) {
        return event.start() != null ? event.start() : event.day().atStartOfDay(zone).toInstant();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
