package com.gradpilot.calendar;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.gradpilot.web.CurrentUser;

/** The private calendar address and the feed itself. */
@RestController
public class CalendarController {

    private static final MediaType CALENDAR = new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8);

    private final CalendarService calendar;

    public CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/api/calendar")
    public Map<String, String> address(@AuthenticationPrincipal Jwt jwt) {
        return calendar.token(CurrentUser.id(jwt)).map(this::urls).orElseGet(HashMap::new);
    }

    /** Turns the feed on, or makes a new address so the old one stops working. */
    @PostMapping("/api/calendar")
    public Map<String, String> newAddress(@AuthenticationPrincipal Jwt jwt) {
        return urls(calendar.newToken(CurrentUser.id(jwt)));
    }

    @DeleteMapping("/api/calendar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void turnOff(@AuthenticationPrincipal Jwt jwt) {
        calendar.turnOff(CurrentUser.id(jwt));
    }

    /** Read by calendar apps. The secret in the address is the only key. */
    @GetMapping("/api/calendar/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        return calendar.feed(token)
                .map(ics -> ResponseEntity.ok()
                        .contentType(CALENDAR)
                        .cacheControl(CacheControl.noStore())
                        .header("X-Robots-Tag", "noindex")
                        .body(ics))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, String> urls(String token) {
        String https = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/calendar/{token}.ics")
                .buildAndExpand(token)
                .toUriString();
        Map<String, String> urls = new HashMap<>();
        urls.put("url", https);
        urls.put("webcal", https.replaceFirst("^https?://", "webcal://"));
        return urls;
    }
}
