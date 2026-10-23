package com.gradpilot.outlook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outlook settings, read from {@code gradpilot.outlook.*}. Outlook is off
 * until a Microsoft app's client id, client secret and redirect address are set.
 *
 * @param clientId      the Application (client) ID of the Microsoft app registration
 * @param clientSecret  a client secret of that app
 * @param redirectUri   this API's callback, e.g. https://api.example.com/api/outlook/callback
 * @param tenant        "common" for work and personal accounts, "consumers" for personal only
 * @param webAppUrl     where to send the person after connecting; the first of several is used
 * @param authorityBase Microsoft sign-in address (changed in tests)
 * @param graphBase     Microsoft Graph address (changed in tests)
 * @param backfillDays  how far back the first sync looks
 * @param maxMessages   the most emails read in one sync
 */
@ConfigurationProperties(prefix = "gradpilot.outlook")
public record OutlookProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tenant,
        String webAppUrl,
        String authorityBase,
        String graphBase,
        Integer backfillDays,
        Integer maxMessages) {

    public OutlookProperties {
        tenant = blank(tenant) ? "common" : tenant.trim();
        authorityBase = trimSlash(blank(authorityBase) ? "https://login.microsoftonline.com" : authorityBase);
        graphBase = trimSlash(blank(graphBase) ? "https://graph.microsoft.com/v1.0" : graphBase);
        webAppUrl = blank(webAppUrl) ? "http://localhost:5173" : trimSlash(webAppUrl.split(",")[0].trim());
        backfillDays = backfillDays == null ? 30 : Math.max(1, Math.min(backfillDays, 365));
        maxMessages = maxMessages == null ? 200 : Math.max(1, maxMessages);
    }

    public boolean configured() {
        return !blank(clientId) && !blank(clientSecret) && !blank(redirectUri);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
