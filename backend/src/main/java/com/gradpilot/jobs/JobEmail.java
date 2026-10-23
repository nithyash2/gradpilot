package com.gradpilot.jobs;

import java.time.Instant;

/**
 * An email that may be about a job application, from a mailbox or pasted in.
 *
 * @param messageId  a stable id for the email, used so the same email is never counted twice
 * @param fromName   the sender's display name, for example "Canva Careers"
 * @param fromAddress the sender's address, for example "careers@canva.com"
 * @param subject    the subject line
 * @param text       the body as plain text
 * @param receivedAt when it arrived
 */
public record JobEmail(
        String messageId,
        String fromName,
        String fromAddress,
        String subject,
        String text,
        Instant receivedAt) {
}
