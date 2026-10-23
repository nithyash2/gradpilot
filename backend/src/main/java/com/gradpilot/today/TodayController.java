package com.gradpilot.today;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.today.TodayDtos.TodayResponse;
import com.gradpilot.web.CurrentUser;

@RestController
public class TodayController {

    private final TodayService today;

    public TodayController(TodayService today) {
        this.today = today;
    }

    @GetMapping("/api/today")
    public TodayResponse today(@AuthenticationPrincipal Jwt jwt) {
        return today.today(CurrentUser.id(jwt));
    }
}
