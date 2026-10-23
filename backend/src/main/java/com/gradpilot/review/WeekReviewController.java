package com.gradpilot.review;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.web.CurrentUser;

@RestController
public class WeekReviewController {

    private final WeekReviewService reviews;

    public WeekReviewController(WeekReviewService reviews) {
        this.reviews = reviews;
    }

    /** The seven days ending on {@code end} (default today). */
    @GetMapping("/api/review/week")
    public WeekReviewService.WeekReview week(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return reviews.week(CurrentUser.id(jwt), end);
    }
}
