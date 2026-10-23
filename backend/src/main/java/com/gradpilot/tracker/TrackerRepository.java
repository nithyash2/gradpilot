package com.gradpilot.tracker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackerRepository extends JpaRepository<Tracker, UUID> {

    List<Tracker> findByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);

    Optional<Tracker> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
