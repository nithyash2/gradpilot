package com.gradpilot.calendar;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Someone's private calendar address. The token itself is never stored, only its hash. */
@Entity
@Table(name = "calendar_feeds")
public class CalendarFeed {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** The token, encrypted. */
    @Column(nullable = false, columnDefinition = "text")
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarFeed() {
        // Required by JPA
    }

    public CalendarFeed(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
