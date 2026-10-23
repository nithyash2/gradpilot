package com.gradpilot.jobs;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.web.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Add or update job applications from an email the person pastes in. */
@RestController
@RequestMapping("/api/jobs")
public class JobEmailController {

    /**
     * An email copied from a mail app. {@code from} and {@code subject} are
     * optional: "From:" and "Subject:" lines in the text are used instead.
     */
    public record PastedEmail(
            @Size(max = 320) String from,
            @Size(max = 500) String subject,
            @NotBlank @Size(max = 100_000) String text,
            Instant receivedAt) {
    }

    private final JobImportService imports;

    public JobEmailController(JobImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/emails")
    public JobImportService.Result importEmail(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody PastedEmail email) {
        JobEmail parsed = EmailText.pasted(email.from(), email.subject(), email.text(), email.receivedAt());
        return imports.importEmail(CurrentUser.id(jwt), parsed);
    }
}
