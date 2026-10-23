package com.gradpilot.outlook;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.web.CurrentUser;

/** Connect Outlook, check on it, sync now, or disconnect. */
@RestController
@RequestMapping("/api/outlook")
public class OutlookController {

    private final OutlookService outlook;

    public OutlookController(OutlookService outlook) {
        this.outlook = outlook;
    }

    @GetMapping("/status")
    public OutlookService.Status status(@AuthenticationPrincipal Jwt jwt) {
        return outlook.status(CurrentUser.id(jwt));
    }

    /** Returns the Microsoft sign-in link; the web app sends the person there. */
    @PostMapping("/connect")
    public Map<String, String> connect(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("url", outlook.connectUrl(CurrentUser.id(jwt)));
    }

    /**
     * Microsoft sends the person's browser here after they allow (or refuse)
     * access. There is no login token on this request: the signed state says
     * who it is for.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String target = outlook.finishConnect(code, state, error);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(target));
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @PostMapping("/sync")
    public OutlookService.SyncSummary sync(@AuthenticationPrincipal Jwt jwt) {
        return outlook.syncNow(CurrentUser.id(jwt));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        outlook.disconnect(userId);
    }
}
