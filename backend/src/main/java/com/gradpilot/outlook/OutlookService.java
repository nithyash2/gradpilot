package com.gradpilot.outlook;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gradpilot.jobs.JobEmail;
import com.gradpilot.jobs.JobEmailKind;
import com.gradpilot.jobs.JobImportService;
import com.gradpilot.jobs.JobImportService.Outcome;
import com.gradpilot.push.PushMessage;
import com.gradpilot.push.PushService;
import com.gradpilot.web.ConflictException;

/**
 * Connects a person's Outlook and reads new job emails from it: on a
 * schedule, and whenever they ask. Interview invitations, assessments and
 * offers also send a notification.
 */
@Service
public class OutlookService {

    private static final Logger log = LoggerFactory.getLogger(OutlookService.class);
    /** Emails can arrive with a slightly earlier timestamp than the last one read. */
    private static final Duration OVERLAP = Duration.ofMinutes(5);

    public record Status(boolean available, boolean connected, String email, boolean needsReconnect,
                         Instant connectedAt, Instant lastSyncedAt, String lastError, int importedCount) {
    }

    public record SyncSummary(int read, int created, int updated, String error) {
        static SyncSummary failed(String error) {
            return new SyncSummary(0, 0, 0, error);
        }
    }

    private final OutlookProperties properties;
    private final OutlookClient client;
    private final OAuthState states;
    private final TokenCipher cipher;
    private final MailboxConnectionRepository connections;
    private final JobImportService imports;
    private final PushService push;
    private final Clock clock;

    public OutlookService(OutlookProperties properties, OutlookClient client, OAuthState states,
                          TokenCipher cipher, MailboxConnectionRepository connections,
                          JobImportService imports, PushService push, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.states = states;
        this.cipher = cipher;
        this.connections = connections;
        this.imports = imports;
        this.push = push;
        this.clock = clock;
    }

    public Status status(UUID userId) {
        return connections.findByUserId(userId)
                .map(c -> new Status(properties.configured(), true, c.getEmail(), c.isNeedsReconnect(),
                        c.getConnectedAt(), c.getLastSyncedAt(), c.getLastError(), c.getImportedCount()))
                .orElseGet(() -> new Status(properties.configured(), false, null, false, null, null, null, 0));
    }

    /** The Microsoft page where the person allows GradPilot to read their mail. */
    public String connectUrl(UUID userId) {
        requireConfigured();
        String state = states.issue(userId);
        return client.authorizeUrl(state, OAuthState.challenge(states.verifier(state)));
    }

    /**
     * Finishes connecting after Microsoft sends the person back, and returns
     * where in the web app to send them.
     */
    public String finishConnect(String code, String state, String error) {
        Optional<UUID> userId = states.verify(state);
        if (userId.isEmpty()) {
            return backToApp("error", "expired");
        }
        if (error != null || code == null || code.isBlank()) {
            return backToApp("error", "access_denied".equals(error) ? "declined" : "failed");
        }
        try {
            OutlookClient.Tokens tokens = client.exchangeCode(code, states.verifier(state));
            String address = client.mailboxAddress(tokens.accessToken());
            MailboxConnection connection = connections.findByUserId(userId.get())
                    .orElseGet(() -> new MailboxConnection(userId.get(), MailboxConnection.OUTLOOK));
            boolean differentMailbox = connection.getEmail() != null && !connection.getEmail().equalsIgnoreCase(address);
            connection.setEmail(address);
            store(connection, tokens);
            connection.setNeedsReconnect(false);
            connection.setLastError(null);
            if (differentMailbox) {
                connection.setLastReceivedAt(null);
            }
            connection.setConnectedAt(clock.instant());
            connections.save(connection);
        } catch (IOException e) {
            log.warn("Could not finish connecting Outlook: {}", e.getMessage());
            return backToApp("error", "failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return backToApp("error", "failed");
        }
        return backToApp("connected", null);
    }

    public void disconnect(UUID userId) {
        connections.findByUserId(userId).ifPresent(connections::delete);
    }

    /** Reads new emails for one person now. */
    public SyncSummary syncNow(UUID userId) {
        requireConfigured();
        MailboxConnection connection = connections.findByUserId(userId)
                .orElseThrow(() -> new ConflictException("Connect Outlook first"));
        return sync(connection);
    }

    /** Reads new emails for everyone with a working connection. */
    public int syncAll() {
        if (!properties.configured()) {
            return 0;
        }
        int imported = 0;
        for (MailboxConnection connection : connections.findByProviderAndNeedsReconnectFalse(MailboxConnection.OUTLOOK)) {
            SyncSummary summary = sync(connection);
            imported += summary.created() + summary.updated();
        }
        return imported;
    }

    SyncSummary sync(MailboxConnection connection) {
        try {
            String accessToken = accessToken(connection);
            Instant since = connection.getLastReceivedAt() != null
                    ? connection.getLastReceivedAt().minus(OVERLAP)
                    : clock.instant().minus(Duration.ofDays(properties.backfillDays()));
            List<OutlookClient.Message> messages = client.inboxSince(accessToken, since, properties.maxMessages());

            int created = 0;
            int updated = 0;
            Instant newest = connection.getLastReceivedAt();
            for (OutlookClient.Message message : messages) {
                if (message.fromAddress() != null && message.fromAddress().equalsIgnoreCase(connection.getEmail())) {
                    continue;
                }
                String id = "outlook:" + (message.internetMessageId() != null ? message.internetMessageId() : message.id());
                JobImportService.Result result = imports.importEmail(connection.getUserId(), new JobEmail(
                        id, message.fromName(), message.fromAddress(), message.subject(), message.text(),
                        message.receivedAt()));
                if (result.outcome() == Outcome.CREATED) {
                    created++;
                } else if (result.outcome() == Outcome.UPDATED) {
                    updated++;
                }
                if (result.outcome() == Outcome.CREATED || result.outcome() == Outcome.UPDATED) {
                    notifyIfImportant(connection.getUserId(), result);
                }
                if (message.receivedAt() != null && (newest == null || message.receivedAt().isAfter(newest))) {
                    newest = message.receivedAt();
                }
            }

            connection.setLastReceivedAt(newest != null ? newest : since);
            connection.setLastSyncedAt(clock.instant());
            connection.setLastError(null);
            connection.addImported(created + updated);
            connections.save(connection);
            if (created + updated > 0) {
                log.info("Outlook sync for {} added {} and updated {} applications",
                        connection.getUserId(), created, updated);
            }
            return new SyncSummary(messages.size(), created, updated, null);
        } catch (OutlookClient.OutlookException e) {
            connection.setLastError(e.reconnectNeeded()
                    ? "Outlook access has ended. Connect it again in Settings."
                    : "Could not reach Outlook. GradPilot will try again soon.");
            connection.setNeedsReconnect(e.reconnectNeeded());
            connection.setLastSyncedAt(clock.instant());
            connections.save(connection);
            log.warn("Outlook sync failed for {}: {}", connection.getUserId(), e.getMessage());
            return SyncSummary.failed(connection.getLastError());
        } catch (IOException e) {
            connection.setLastError("Could not reach Outlook. GradPilot will try again soon.");
            connections.save(connection);
            log.warn("Outlook sync failed for {}: {}", connection.getUserId(), e.getMessage());
            return SyncSummary.failed(connection.getLastError());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyncSummary.failed("Sync was interrupted");
        }
    }

    private String accessToken(MailboxConnection connection) throws IOException, InterruptedException {
        Instant now = clock.instant();
        if (connection.getAccessToken() != null && connection.getAccessExpiresAt() != null
                && connection.getAccessExpiresAt().isAfter(now.plusSeconds(60))) {
            return cipher.decrypt(connection.getAccessToken());
        }
        OutlookClient.Tokens tokens = client.refresh(cipher.decrypt(connection.getRefreshToken()));
        store(connection, tokens);
        connections.save(connection);
        return tokens.accessToken();
    }

    private void store(MailboxConnection connection, OutlookClient.Tokens tokens) {
        connection.setAccessToken(cipher.encrypt(tokens.accessToken()));
        connection.setAccessExpiresAt(tokens.expiresAt());
        if (tokens.refreshToken() != null) {
            connection.setRefreshToken(cipher.encrypt(tokens.refreshToken()));
        }
    }

    /** Good news and next steps deserve a notification; rejections can wait for the app. */
    private void notifyIfImportant(UUID userId, JobImportService.Result result) {
        String title = switch (result.kind() == null ? JobEmailKind.APPLIED : result.kind()) {
            case INTERVIEW -> "Interview invitation";
            case ASSESSMENT -> "Assessment to complete";
            case OFFER -> "Job offer!";
            default -> null;
        };
        if (title == null) {
            return;
        }
        String body = result.title() != null ? result.title() : "Open GradPilot to see it";
        try {
            push.sendToUser(userId, PushMessage.of(title, body, "/trackers/" + result.trackerId(),
                    "job-" + result.itemId()));
        } catch (RuntimeException e) {
            // A failed notification must not stop the sync
            log.warn("Could not send a job notification: {}", e.getMessage());
        }
    }

    private String backToApp(String outcome, String reason) {
        String url = properties.webAppUrl() + "/settings?outlook=" + outcome;
        return reason == null ? url : url + "&reason=" + URLEncoder.encode(reason, StandardCharsets.UTF_8);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new OutlookUnavailableException();
        }
    }
}
