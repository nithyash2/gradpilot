package com.gradpilot.today;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.gradpilot.checkin.CheckIn;
import com.gradpilot.checkin.CheckInDtos.CheckInResponse;
import com.gradpilot.checkin.CheckInRepository;
import com.gradpilot.today.TodayDtos.CountdownView;
import com.gradpilot.today.TodayDtos.DayMark;
import com.gradpilot.today.TodayDtos.ShiftView;
import com.gradpilot.today.TodayDtos.TodayResponse;
import com.gradpilot.today.TodayDtos.TrackedItem;
import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.ItemRepository;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerRepository;
import com.gradpilot.tracker.TrackerType;
import com.gradpilot.tracker.dto.TrackerDtos.ItemResponse;
import com.gradpilot.user.User;
import com.gradpilot.user.UserRepository;

/** Builds the Today screen: next shift, things due, countdowns and the check-in. */
@Service
public class TodayService {

    static final int MAX_COUNTDOWNS = 5;
    private static final List<TrackerType> TASK_TYPES =
            List.of(TrackerType.CHECKLIST, TrackerType.PIPELINE, TrackerType.PROGRESS);

    private final UserRepository users;
    private final TrackerRepository trackers;
    private final ItemRepository items;
    private final CheckInRepository checkIns;
    private final Clock clock;

    public TodayService(UserRepository users, TrackerRepository trackers, ItemRepository items,
                        CheckInRepository checkIns, Clock clock) {
        this.users = users;
        this.trackers = trackers;
        this.items = items;
        this.checkIns = checkIns;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ZoneId zone = ZoneId.of(user.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);

        Map<UUID, Tracker> byId = trackers.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId).stream()
                .collect(Collectors.toMap(Tracker::getId, Function.identity()));

        ShiftView nextShift = nextShift(items.findDatedFrom(userId, TrackerType.SCHEDULE, date), byId, date, time);

        List<TrackedItem> dueToday = items.findOpenOnDay(userId, date, TASK_TYPES).stream()
                .map(i -> new TrackedItem(i.getTrackerId(), name(byId, i), ItemResponse.from(i)))
                .toList();

        List<CountdownView> countdowns = items.findDatedFrom(userId, TrackerType.COUNTDOWN, date).stream()
                .limit(MAX_COUNTDOWNS)
                .map(i -> new CountdownView(i.getTrackerId(), name(byId, i), i.getId(), i.getTitle(),
                        i.getItemDate(), ChronoUnit.DAYS.between(date, i.getItemDate())))
                .toList();

        CheckInResponse checkin = checkIns.findByUserIdAndDay(userId, date)
                .map(CheckInResponse::from)
                .orElseGet(() -> CheckInResponse.empty(date));

        return new TodayResponse(date, time, zone.getId(), nextShift, dueToday, countdowns, checkin,
                lastSevenDays(userId, date));
    }

    /** First shift that has not finished yet. Overnight shifts count as not finished on their start day. */
    static ShiftView nextShift(List<Item> shifts, Map<UUID, Tracker> trackersById, LocalDate today, LocalTime now) {
        for (Item shift : shifts) {
            if (shift.getItemDate().equals(today) && hasFinished(shift, now)) {
                continue;
            }
            Tracker tracker = trackersById.get(shift.getTrackerId());
            int travel = tracker == null ? 0 : tracker.travelMinutes();
            LocalTime leaveAt = shift.getStartTime() == null ? null : shift.getStartTime().minusMinutes(travel);
            return new ShiftView(shift.getTrackerId(), tracker == null ? "" : tracker.getName(),
                    ItemResponse.from(shift), leaveAt, shift.getItemDate().equals(today));
        }
        return null;
    }

    private static boolean hasFinished(Item shift, LocalTime now) {
        LocalTime start = shift.getStartTime();
        LocalTime end = shift.getEndTime();
        if (start == null) {
            return false;
        }
        if (end == null) {
            return start.isBefore(now);
        }
        boolean overnight = end.isBefore(start);
        return !overnight && end.isBefore(now);
    }

    private List<DayMark> lastSevenDays(UUID userId, LocalDate today) {
        LocalDate from = today.minusDays(6);
        Map<LocalDate, Boolean> done = checkIns.findByUserIdAndDayBetweenOrderByDayAsc(userId, from, today)
                .stream()
                .collect(Collectors.toMap(CheckIn::getDay, CheckIn::isFocusDone));
        List<DayMark> marks = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            marks.add(new DayMark(d, done.getOrDefault(d, false)));
        }
        return marks;
    }

    private static String name(Map<UUID, Tracker> byId, Item item) {
        Tracker tracker = byId.get(item.getTrackerId());
        return tracker == null ? "" : tracker.getName();
    }
}
