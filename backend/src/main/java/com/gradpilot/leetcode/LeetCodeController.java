package com.gradpilot.leetcode;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.web.CurrentUser;

@RestController
public class LeetCodeController {

    private final LeetCodeService leetcode;

    public LeetCodeController(LeetCodeService leetcode) {
        this.leetcode = leetcode;
    }

    /** Reads the latest solved counts for a LeetCode tracker now. */
    @PostMapping("/api/trackers/{trackerId}/leetcode/sync")
    public LeetCodeService.SyncResult sync(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId) {
        return leetcode.syncForUser(CurrentUser.id(jwt), trackerId);
    }
}
