package com.gradpilot.outlook;

/** Outlook is not set up on this server (no Microsoft app details). */
public class OutlookUnavailableException extends RuntimeException {

    public OutlookUnavailableException() {
        super("Outlook is not set up on this server yet");
    }
}
