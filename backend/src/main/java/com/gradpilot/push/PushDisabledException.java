package com.gradpilot.push;

public class PushDisabledException extends RuntimeException {

    public PushDisabledException() {
        super("Notifications are not set up on this server yet");
    }
}
