package com.gradpilot.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gradpilot.push.PushMessage;
import com.gradpilot.review.WeekReviewService.Jobs;
import com.gradpilot.review.WeekReviewService.Shifts;
import com.gradpilot.review.WeekReviewService.WeekReview;

class WeekReviewMessageTest {

    private static WeekReview review(Jobs jobs, int leetcode, Shifts shifts, int focusDays) {
        LocalDate end = LocalDate.of(2026, 10, 25);
        return new WeekReview(end.minusDays(6), end, List.of(), focusDays, focusDays, jobs, leetcode, shifts,
                List.of(), List.of());
    }

    @Test
    void sundayMessageListsOnlyWhatHappened() {
        PushMessage message = WeekReviewService.message(review(new Jobs(5, 0, 2, 0, 1, 7), 12, new Shifts(3, 16.5), 4));

        assertThat(message.title()).isEqualTo("Your week");
        assertThat(message.body())
                .isEqualTo("5 applications · 2 interviews · 12 LeetCode · 3 shifts · 4/7 days with your one thing done");
        assertThat(message.url()).isEqualTo("/review");
    }

    @Test
    void quietWeeksStillGetAKindMessage() {
        PushMessage message = WeekReviewService.message(review(new Jobs(0, 0, 0, 0, 0, 0), 0, new Shifts(0, 0), 0));

        assertThat(message.body()).isEqualTo("0/7 days with your one thing done");
    }

    @Test
    void shiftsThatEndAfterMidnightCountTheRightHours() {
        assertThat(WeekReviewService.minutes(LocalTime.of(17, 0), LocalTime.of(22, 30))).isEqualTo(330);
        assertThat(WeekReviewService.minutes(LocalTime.of(22, 0), LocalTime.of(2, 0))).isEqualTo(240);
        assertThat(WeekReviewService.minutes(LocalTime.of(9, 0), null)).isZero();
    }
}
