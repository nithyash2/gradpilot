package com.gradpilot.jobs;

/**
 * What the parser read from a job email.
 *
 * @param kind    what happened
 * @param company the employer, or null when it could not be found
 * @param role    the job title, or null when it could not be found
 * @param source  where the application was made: SEEK, LinkedIn, Indeed, Company site or Email
 */
public record JobEmailFinding(JobEmailKind kind, String company, String role, String source) {
}
