package com.gradpilot.push;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Push settings, read from {@code gradpilot.push.*}.
 *
 * @param publicKey     VAPID public key (base64url). Push is off when blank.
 * @param privateKey    VAPID private key (base64url). Push is off when blank.
 * @param subject       contact for push services, mailto: or https:
 * @param allowedHosts  push service hosts the server may call; "*.example.com" matches subdomains
 * @param allowInsecure allow http:// endpoints (tests only)
 */
@ConfigurationProperties(prefix = "gradpilot.push")
public record PushProperties(
        String publicKey,
        String privateKey,
        String subject,
        List<String> allowedHosts,
        boolean allowInsecure) {

    public PushProperties {
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
    }

    public boolean configured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }

    /** Only real push services may be called, so the server cannot be pointed at internal addresses. */
    public boolean hostAllowed(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase();
        for (String pattern : allowedHosts) {
            String p = pattern.trim().toLowerCase();
            if (p.startsWith("*.") ? h.endsWith(p.substring(1)) : h.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
