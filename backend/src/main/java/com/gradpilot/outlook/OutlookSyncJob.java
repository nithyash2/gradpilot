package com.gradpilot.outlook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reads new job emails from every connected Outlook every 15 minutes. */
@Component
@ConditionalOnProperty(name = "gradpilot.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutlookSyncJob {

    private static final Logger log = LoggerFactory.getLogger(OutlookSyncJob.class);

    private final OutlookService outlook;

    public OutlookSyncJob(OutlookService outlook) {
        this.outlook = outlook;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void run() {
        try {
            int changed = outlook.syncAll();
            if (changed > 0) {
                log.info("Outlook sync added or updated {} applications", changed);
            }
        } catch (RuntimeException e) {
            log.error("Outlook sync run failed", e);
        }
    }
}
