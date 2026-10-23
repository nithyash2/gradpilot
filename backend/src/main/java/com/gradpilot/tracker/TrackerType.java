package com.gradpilot.tracker;

/**
 * The six kinds of tracker a person can create with the + button.
 */
public enum TrackerType {
    /** Steps to tick off, e.g. licence steps. */
    CHECKLIST,
    /** Items that move through named stages, e.g. job applications. */
    PIPELINE,
    /** Topics with a percentage, e.g. courses. */
    PROGRESS,
    /** Dated entries with an amount, e.g. lesson hours. */
    LOG,
    /** Target dates to count down to. */
    COUNTDOWN,
    /** Dated time slots, e.g. work shifts. */
    SCHEDULE
}
