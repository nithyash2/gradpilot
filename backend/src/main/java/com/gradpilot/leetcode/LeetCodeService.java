package com.gradpilot.leetcode;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerRepository;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;
import com.gradpilot.web.NotFoundException;

/**
 * Keeps a LeetCode tracker up to date. The first sync records where the
 * person is up to; after that, each new problem solved is logged on the day
 * it was noticed, so the tracker shows a daily practice log and a streak.
 */
@Service
public class LeetCodeService {

    public static final String TEMPLATE = "leetcode";
    static final String SETTING_USERNAME = "leetcodeUsername";
    static final String SETTING_STATS = "leetcode";
    static final String ITEM_TITLE = "Solved on LeetCode";

    private static final Logger log = LoggerFactory.getLogger(LeetCodeService.class);

    public record SyncResult(String username, Integer easy, Integer medium, Integer hard, Integer total,
                             int newToday, String error) {
    }

    private final LeetCodeClient client;
    private final TrackerRepository trackers;
    private final ItemRepository items;
    private final UserRepository users;
    private final Clock clock;

    public LeetCodeService(LeetCodeClient client, TrackerRepository trackers, ItemRepository items,
                           UserRepository users, Clock clock) {
        this.client = client;
        this.trackers = trackers;
        this.items = items;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public SyncResult syncForUser(UUID userId, UUID trackerId) {
        Tracker tracker = trackers.findByIdAndUserId(trackerId, userId)
                .orElseThrow(() -> new NotFoundException("Tracker"));
        if (!TEMPLATE.equals(tracker.getTemplate())) {
            throw new IllegalArgumentException("This tracker is not a LeetCode tracker");
        }
        return sync(tracker);
    }

    /** Syncs every LeetCode tracker; one failure does not stop the others. */
    public int syncAll() {
        int changed = 0;
        for (Tracker tracker : trackers.findByTemplate(TEMPLATE)) {
            try {
                if (syncOne(tracker.getId()).newToday() > 0) {
                    changed++;
                }
            } catch (RuntimeException e) {
                log.warn("LeetCode sync failed for tracker {}: {}", tracker.getId(), e.getMessage());
            }
        }
        return changed;
    }

    @Transactional
    public SyncResult syncOne(UUID trackerId) {
        return trackers.findById(trackerId).map(this::sync)
                .orElse(new SyncResult(null, null, null, null, null, 0, "Tracker not found"));
    }

    SyncResult sync(Tracker tracker) {
        Object raw = tracker.getSettings().get(SETTING_USERNAME);
        String username = raw instanceof String s ? s.trim() : "";
        if (username.isEmpty()) {
            return new SyncResult(null, null, null, null, null, 0, "Add your LeetCode username first");
        }

        Optional<LeetCodeClient.Solved> fetched;
        try {
            fetched = client.solved(username);
        } catch (IOException e) {
            return new SyncResult(username, null, null, null, null, 0,
                    "Could not reach LeetCode right now. GradPilot will try again later.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SyncResult(username, null, null, null, null, 0, "Sync was interrupted");
        }
        if (fetched.isEmpty()) {
            return new SyncResult(username, null, null, null, null, 0,
                    "LeetCode has no user called " + username);
        }
        LeetCodeClient.Solved solved = fetched.get();

        Map<String, Object> previous = previousStats(tracker, username);
        int newSolved = 0;
        if (previous != null && previous.get("total") instanceof Number before) {
            newSolved = Math.max(0, solved.total() - before.intValue());
        }
        LocalDate today = LocalDate.now(clock.withZone(zone(tracker.getUserId())));
        if (newSolved > 0) {
            logSolved(tracker, today, newSolved);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("username", username);
        stats.put("easy", solved.easy());
        stats.put("medium", solved.medium());
        stats.put("hard", solved.hard());
        stats.put("total", solved.total());
        stats.put("syncedAt", clock.instant().toString());
        Map<String, Object> settings = new LinkedHashMap<>(tracker.getSettings());
        settings.put(SETTING_STATS, stats);
        tracker.setSettings(settings);
        trackers.save(tracker);

        int solvedToday = todayAmount(tracker, today);
        return new SyncResult(username, solved.easy(), solved.medium(), solved.hard(), solved.total(),
                solvedToday, null);
    }

    /** Stats from the last sync, only when they are for the same username. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> previousStats(Tracker tracker, String username) {
        Object value = tracker.getSettings().get(SETTING_STATS);
        if (value instanceof Map<?, ?> map && username.equalsIgnoreCase(String.valueOf(map.get("username")))) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private void logSolved(Tracker tracker, LocalDate day, int count) {
        Item entry = findEntry(tracker, day).orElseGet(() -> {
            Item item = new Item(tracker.getId(), ITEM_TITLE);
            item.setItemDate(day);
            item.setAmount(BigDecimal.ZERO);
            item.setFields(Map.of("source", "leetcode"));
            item.setSortOrder((int) items.countByTrackerId(tracker.getId()));
            return item;
        });
        BigDecimal current = entry.getAmount() == null ? BigDecimal.ZERO : entry.getAmount();
        entry.setAmount(current.add(BigDecimal.valueOf(count)));
        items.save(entry);
    }

    private int todayAmount(Tracker tracker, LocalDate day) {
        return findEntry(tracker, day)
                .map(Item::getAmount)
                .map(BigDecimal::intValue)
                .orElse(0);
    }

    private Optional<Item> findEntry(Tracker tracker, LocalDate day) {
        List<Item> all = items.findByTrackerIdOrderBySortOrderAscCreatedAtAsc(tracker.getId());
        return all.stream()
                .filter(i -> day.equals(i.getItemDate()) && "leetcode".equals(i.getFields().get("source")))
                .findFirst();
    }

    private ZoneId zone(UUID userId) {
        return users.findById(userId)
                .map(User::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneId.of(User.DEFAULT_TIMEZONE));
    }
}
