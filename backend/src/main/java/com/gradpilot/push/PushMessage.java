package com.gradpilot.push;

/**
 * What a notification shows. The service worker in the web app reads
 * these fields and opens {@code url} when the notification is tapped.
 *
 * @param urgent sent with high urgency, e.g. "time to leave for work"
 */
public record PushMessage(String title, String body, String url, String tag, boolean urgent) {

    public static PushMessage of(String title, String body, String url, String tag) {
        return new PushMessage(title, body, url, tag, false);
    }
}
