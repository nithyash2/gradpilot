package com.gradpilot.leetcode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks every LeetCode tracker once an hour. */
@Component
@ConditionalOnProperty(name = "gradpilot.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class LeetCodeSyncJob {

    private static final Logger log = LoggerFactory.getLogger(LeetCodeSyncJob.class);

    private final LeetCodeService leetcode;

    public LeetCodeSyncJob(LeetCodeService leetcode) {
        this.leetcode = leetcode;
    }

    @Scheduled(cron = "0 7 * * * *")
    public void run() {
        try {
            int changed = leetcode.syncAll();
            if (changed > 0) {
                log.info("LeetCode sync logged new problems on {} trackers", changed);
            }
        } catch (RuntimeException e) {
            log.error("LeetCode sync run failed", e);
        }
    }
}
