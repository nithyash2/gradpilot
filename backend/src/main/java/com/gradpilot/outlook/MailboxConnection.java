package com.gradpilot.outlook;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A connected mailbox. Token columns hold encrypted values only. */
@Entity
@Table(name = "mailbox_connections")
public class MailboxConnection {

    public static final String OUTLOOK = "outlook";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(length = 320)
    private String email;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "text")
    private String refreshToken;

    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "needs_reconnect", nullable = false)
    private boolean needsReconnect;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_received_at")
    private Instant lastReceivedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MailboxConnection() {
        // Required by JPA
    }

    public MailboxConnection(UUID userId, String provider) {
        this.userId = userId;
        this.provider = provider;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (connectedAt == null) {
            connectedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(Instant accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public boolean isNeedsReconnect() {
        return needsReconnect;
    }

    public void setNeedsReconnect(boolean needsReconnect) {
        this.needsReconnect = needsReconnect;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getLastReceivedAt() {
        return lastReceivedAt;
    }

    public void setLastReceivedAt(Instant lastReceivedAt) {
        this.lastReceivedAt = lastReceivedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null || lastError.length() <= 500 ? lastError : lastError.substring(0, 500);
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void addImported(int count) {
        this.importedCount += count;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }
}
