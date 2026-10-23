package com.gradpilot.checkin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A person's daily check-in: wake time, meals, self-care and today's one thing. */
@Entity
@Table(name = "checkins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(name = "wake_time")
    private LocalTime wakeTime;

    @Column(length = 200)
    private String breakfast;

    @Column(length = 200)
    private String lunch;

    @Column(length = 200)
    private String dinner;

    @Column(name = "self_care", nullable = false)
    private boolean selfCare;

    @Column(nullable = false)
    private boolean dishes;

    @Column(name = "bed_on_time", nullable = false)
    private boolean bedOnTime;

    @Column(length = 200)
    private String focus;

    @Column(name = "focus_done", nullable = false)
    private boolean focusDone;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CheckIn() {
        // Required by JPA
    }

    public CheckIn(UUID userId, LocalDate day) {
        this.userId = userId;
        this.day = day;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
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

    public LocalDate getDay() {
        return day;
    }

    public LocalTime getWakeTime() {
        return wakeTime;
    }

    public void setWakeTime(LocalTime wakeTime) {
        this.wakeTime = wakeTime;
    }

    public String getBreakfast() {
        return breakfast;
    }

    public void setBreakfast(String breakfast) {
        this.breakfast = breakfast;
    }

    public String getLunch() {
        return lunch;
    }

    public void setLunch(String lunch) {
        this.lunch = lunch;
    }

    public String getDinner() {
        return dinner;
    }

    public void setDinner(String dinner) {
        this.dinner = dinner;
    }

    public boolean isSelfCare() {
        return selfCare;
    }

    public void setSelfCare(boolean selfCare) {
        this.selfCare = selfCare;
    }

    public boolean isDishes() {
        return dishes;
    }

    public void setDishes(boolean dishes) {
        this.dishes = dishes;
    }

    public boolean isBedOnTime() {
        return bedOnTime;
    }

    public void setBedOnTime(boolean bedOnTime) {
        this.bedOnTime = bedOnTime;
    }

    public String getFocus() {
        return focus;
    }

    public void setFocus(String focus) {
        this.focus = focus;
    }

    public boolean isFocusDone() {
        return focusDone;
    }

    public void setFocusDone(boolean focusDone) {
        this.focusDone = focusDone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
