package com.gradpilot.checkin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gradpilot.checkin.CheckInDtos.CheckInRequest;
import com.gradpilot.checkin.CheckInDtos.CheckInResponse;
import com.gradpilot.web.ConflictException;

@Service
public class CheckInService {

    static final int MAX_RANGE_DAYS = 62;

    private final CheckInRepository checkIns;

    public CheckInService(CheckInRepository checkIns) {
        this.checkIns = checkIns;
    }

    @Transactional(readOnly = true)
    public CheckInResponse get(UUID userId, LocalDate day) {
        return checkIns.findByUserIdAndDay(userId, day)
                .map(CheckInResponse::from)
                .orElseGet(() -> CheckInResponse.empty(day));
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> range(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Ask for at most " + MAX_RANGE_DAYS + " days at a time");
        }
        return checkIns.findByUserIdAndDayBetweenOrderByDayAsc(userId, from, to).stream()
                .map(CheckInResponse::from)
                .toList();
    }

    /** Creates or replaces the check-in for one day. */
    @Transactional
    public CheckInResponse save(UUID userId, LocalDate day, CheckInRequest request) {
        CheckIn checkIn = checkIns.findByUserIdAndDay(userId, day)
                .orElseGet(() -> new CheckIn(userId, day));
        checkIn.setWakeTime(request.wakeTime());
        checkIn.setBreakfast(trimToNull(request.breakfast()));
        checkIn.setLunch(trimToNull(request.lunch()));
        checkIn.setDinner(trimToNull(request.dinner()));
        checkIn.setSelfCare(Boolean.TRUE.equals(request.selfCare()));
        checkIn.setDishes(Boolean.TRUE.equals(request.dishes()));
        checkIn.setBedOnTime(Boolean.TRUE.equals(request.bedOnTime()));
        checkIn.setFocus(trimToNull(request.focus()));
        checkIn.setFocusDone(Boolean.TRUE.equals(request.focusDone()));
        checkIn.setNotes(trimToNull(request.notes()));
        try {
            return CheckInResponse.from(checkIns.saveAndFlush(checkIn));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This day was saved from another device. Reload and try again.");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
