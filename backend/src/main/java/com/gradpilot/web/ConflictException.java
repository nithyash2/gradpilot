package com.gradpilot.web;

/** The request clashes with data saved at the same moment elsewhere. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
