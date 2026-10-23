package com.gradpilot.tracker;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByTrackerIdOrderBySortOrderAscCreatedAtAsc(UUID trackerId);

    List<Item> findByTrackerIdIn(Collection<UUID> trackerIds);

    Optional<Item> findByIdAndTrackerId(UUID id, UUID trackerId);

    long countByTrackerId(UUID trackerId);

    /** Dated items of one tracker type from a given day onwards, earliest first. */
    @Query("""
            select i from Item i join Tracker t on t.id = i.trackerId
            where t.userId = :userId and t.type = :type
              and i.itemDate is not null and i.itemDate >= :from
            order by i.itemDate asc, i.startTime asc nulls last, i.createdAt asc
            """)
    List<Item> findDatedFrom(@Param("userId") UUID userId,
                             @Param("type") TrackerType type,
                             @Param("from") LocalDate from);

    /** Unfinished items dated on a given day, across task-like trackers. */
    @Query("""
            select i from Item i join Tracker t on t.id = i.trackerId
            where t.userId = :userId and i.itemDate = :day and i.done = false
              and t.type in :types
            order by i.startTime asc nulls last, i.createdAt asc
            """)
    List<Item> findOpenOnDay(@Param("userId") UUID userId,
                             @Param("day") LocalDate day,
                             @Param("types") Collection<TrackerType> types);

    /** Reminders that are due and have not been sent yet. */
    @Query("""
            select i from Item i
            where i.reminderAt is not null and i.reminderAt <= :now and i.reminderSentAt is null
            order by i.reminderAt asc
            """)
    List<Item> findDueReminders(@Param("now") Instant now);
}
