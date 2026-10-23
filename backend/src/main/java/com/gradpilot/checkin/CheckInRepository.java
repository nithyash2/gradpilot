package com.gradpilot.checkin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Optional<CheckIn> findByUserIdAndDay(UUID userId, LocalDate day);

    List<CheckIn> findByUserIdAndDayBetweenOrderByDayAsc(UUID userId, LocalDate from, LocalDate to);
}
