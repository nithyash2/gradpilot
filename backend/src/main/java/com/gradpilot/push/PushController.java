package com.gradpilot.push;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.web.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    /** Matches the JSON a browser's PushSubscription produces. */
    public record SubscribeRequest(
            @NotBlank @Size(max = 2000) String endpoint,
            @NotNull @Valid Keys keys) {

        public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
        }
    }

    public record PushStatus(boolean enabled, String publicKey, int devices) {
    }

    @GetMapping("/status")
    public PushStatus status(@AuthenticationPrincipal Jwt jwt) {
        return new PushStatus(push.enabled(), push.publicKey(), push.countDevices(CurrentUser.id(jwt)));
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscribeRequest request,
                          @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        push.subscribe(CurrentUser.id(jwt), request.endpoint(), request.keys().p256dh(), request.keys().auth(),
                userAgent);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Jwt jwt, @RequestParam String endpoint) {
        push.unsubscribe(CurrentUser.id(jwt), endpoint);
    }

    @PostMapping("/test")
    public Map<String, Integer> test(@AuthenticationPrincipal Jwt jwt) {
        if (!push.enabled()) {
            throw new PushDisabledException();
        }
        int delivered = push.sendToUser(CurrentUser.id(jwt),
                PushMessage.of("GradPilot", "Notifications are working.", "/", "test"));
        return Map.of("delivered", delivered);
    }
}
