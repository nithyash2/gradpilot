package com.gradpilot.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs once a minute and sends whatever notifications are due.
 * Turn off with {@code gradpilot.scheduler.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "gradpilot.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);

    private final NotificationService notifications;

    public NotificationJob(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 * * * * *")
    public void run() {
        try {
            int digests = notifications.sendDailyDigests();
            int reminders = notifications.sendDueReminders();
            if (digests + reminders > 0) {
                log.info("Sent {} daily digests and {} reminders", digests, reminders);
            }
        } catch (RuntimeException e) {
            // One bad run must not stop the next one
            log.error("Notification run failed", e);
        }
    }
}
