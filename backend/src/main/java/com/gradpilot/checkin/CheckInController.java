package com.gradpilot.checkin;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.checkin.CheckInDtos.CheckInRequest;
import com.gradpilot.checkin.CheckInDtos.CheckInResponse;
import com.gradpilot.web.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/checkins")
public class CheckInController {

    private final CheckInService service;

    public CheckInController(CheckInService service) {
        this.service = service;
    }

    @GetMapping
    public List<CheckInResponse> range(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.range(CurrentUser.id(jwt), from, to);
    }

    @GetMapping("/{day}")
    public CheckInResponse get(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return service.get(CurrentUser.id(jwt), day);
    }

    @PutMapping("/{day}")
    public CheckInResponse save(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                @Valid @RequestBody CheckInRequest request) {
        return service.save(CurrentUser.id(jwt), day, request);
    }
}
