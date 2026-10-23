package com.gradpilot.outlook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, UUID> {

    Optional<MailboxConnection> findByUserId(UUID userId);

    List<MailboxConnection> findByProviderAndNeedsReconnectFalse(String provider);

    void deleteByUserId(UUID userId);
}
