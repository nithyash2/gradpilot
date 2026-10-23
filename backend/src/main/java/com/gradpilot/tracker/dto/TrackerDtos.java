package com.gradpilot.tracker.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gradpilot.tracker.Item;
import com.gradpilot.tracker.Tracker;
import com.gradpilot.tracker.TrackerType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies for trackers and items. */
public final class TrackerDtos {

    private TrackerDtos() {
    }

    /** Create from a template ({@code template}) or from scratch ({@code name} + {@code type}). */
    public record CreateTrackerRequest(
            @Size(max = 100) String name,
            TrackerType type,
            @Size(max = 40) String template,
            @Size(max = 20) String colour,
            Map<String, Object> settings) {
    }

    public record UpdateTrackerRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 20) String colour,
            Map<String, Object> settings,
            Integer sortOrder) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String notes,
            Boolean done,
            @Size(max = 50) String stage,
            @Min(0) @Max(100) Integer progress,
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            LocalDate itemDate,
            LocalTime startTime,
            LocalTime endTime,
            Instant reminderAt,
            Map<String, Object> fields,
            Integer sortOrder) {
    }

    public record TrackerStats(
            int total,
            int done,
            Integer averageProgress,
            BigDecimal totalAmount,
            Map<String, Integer> byStage,
            LocalDate nextDate) {
    }

    public record TrackerResponse(
            UUID id,
            String name,
            TrackerType type,
            String template,
            String colour,
            Map<String, Object> settings,
            int sortOrder,
            TrackerStats stats) {

        public static TrackerResponse from(Tracker tracker, TrackerStats stats) {
            return new TrackerResponse(tracker.getId(), tracker.getName(), tracker.getType(),
                    tracker.getTemplate(), tracker.getColour(), tracker.getSettings(),
                    tracker.getSortOrder(), stats);
        }
    }

    public record ItemResponse(
            UUID id,
            UUID trackerId,
            String title,
            String notes,
            boolean done,
            String stage,
            Integer progress,
            BigDecimal amount,
            LocalDate itemDate,
            LocalTime startTime,
            LocalTime endTime,
            Instant reminderAt,
            Map<String, Object> fields,
            int sortOrder) {

        public static ItemResponse from(Item item) {
            return new ItemResponse(item.getId(), item.getTrackerId(), item.getTitle(), item.getNotes(),
                    item.isDone(), item.getStage(), item.getProgress(), item.getAmount(), item.getItemDate(),
                    item.getStartTime(), item.getEndTime(), item.getReminderAt(), item.getFields(),
                    item.getSortOrder());
        }
    }

    public record TrackerDetailResponse(TrackerResponse tracker, List<ItemResponse> items) {
    }
}
