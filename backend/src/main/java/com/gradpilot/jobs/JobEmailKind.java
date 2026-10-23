package com.gradpilot.jobs;

/** What a job email says happened, mapped to a Job hunt stage. */
public enum JobEmailKind {
    APPLIED("Applied"),
    ASSESSMENT("Assessment"),
    INTERVIEW("Interview"),
    OFFER("Offer"),
    REJECTED("Rejected");

    private final String stage;

    JobEmailKind(String stage) {
        this.stage = stage;
    }

    /** The Job hunt stage this kind of email moves an application to. */
    public String stage() {
        return stage;
    }
}
