package com.gradpilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Real-world shaped emails from job boards and careers sites. Names and
 * wording follow the patterns these senders use; no real email is stored.
 */
class JobEmailParserTest {

    private final JobEmailParser parser = new JobEmailParser();

    private Optional<JobEmailFinding> read(String fromName, String fromAddress, String subject, String text) {
        return parser.parse(new JobEmail("id", fromName, fromAddress, subject, text, Instant.EPOCH));
    }

    @Test
    void linkedInEasyApplyConfirmation() {
        var finding = read("LinkedIn", "jobs-noreply@linkedin.com",
                "Nithya, your application was sent to Canva",
                """
                Your application was sent to Canva
                Graduate Software Engineer
                Canva · Melbourne, Victoria, Australia
                Applied on 23 October 2026
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.APPLIED);
        assertThat(finding.company()).isEqualTo("Canva");
        assertThat(finding.source()).isEqualTo("LinkedIn");
    }

    @Test
    void seekConfirmationWithRoleAndCompany() {
        var finding = read("SEEK", "noreply@s.seek.com.au",
                "Your application was successfully submitted",
                """
                Hi Nithyashree,
                Thanks for applying for the Junior Data Analyst role at Australia Post.
                Your application has been sent and the employer will be in touch if you are shortlisted.
                Good luck!
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.APPLIED);
        assertThat(finding.role()).isEqualTo("Junior Data Analyst");
        assertThat(finding.company()).isEqualTo("Australia Post");
        assertThat(finding.source()).isEqualTo("SEEK");
    }

    @Test
    void indeedConfirmation() {
        var finding = read("Indeed Apply", "indeedapply@indeed.com",
                "Indeed Application: IT Support Officer",
                """
                Application submitted
                IT Support Officer
                Your application was sent to Monash University.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.APPLIED);
        assertThat(finding.role()).isEqualTo("IT Support Officer");
        assertThat(finding.company()).isEqualTo("Monash University");
        assertThat(finding.source()).isEqualTo("Indeed");
    }

    @Test
    void careersSiteConfirmationThatMentionsRejectionOnlyAsAPossibility() {
        var finding = read("Telstra Careers", "telstra@myworkday.com",
                "Thank you for applying",
                """
                Dear Nithyashree,
                Thank you for your application for the position of Graduate Machine Learning Engineer.
                Due to the high volume of applications, unfortunately only shortlisted candidates will be contacted.
                If you have not heard from us within two weeks, please assume you have not been successful.
                Kind regards,
                Telstra Talent Acquisition
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.APPLIED);
        assertThat(finding.role()).isEqualTo("Graduate Machine Learning Engineer");
        assertThat(finding.company()).isEqualTo("Telstra");
        assertThat(finding.source()).isEqualTo("Company site");
    }

    @Test
    void greenhouseRejection() {
        var finding = read("Atlassian Recruiting", "no-reply@us.greenhouse-mail.io",
                "Your application to Atlassian",
                """
                Hi Nithyashree,
                Thank you for your interest in the Associate Software Engineer role at Atlassian.
                After careful consideration, we have decided to move forward with other candidates
                whose experience more closely matches our needs at this time.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.REJECTED);
        assertThat(finding.company()).isEqualTo("Atlassian");
        assertThat(finding.role()).isEqualTo("Associate Software Engineer");
        assertThat(finding.source()).isEqualTo("Company site");
    }

    @Test
    void plainUnfortunatelyRejection() {
        var finding = read("REA Group Careers", "careers@rea-group.com",
                "Your REA Group application",
                """
                Thank you for taking the time to apply. Unfortunately, on this occasion your application
                will not be progressing further.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.REJECTED);
        assertThat(finding.company()).isEqualTo("REA Group");
    }

    @Test
    void interviewInvitation() {
        var finding = read("Priya from Canva", "priya.recruiter@canva.com",
                "Interview invitation - Graduate Software Engineer",
                """
                Hi Nithyashree,
                Thanks for applying for the Graduate Software Engineer position at Canva.
                We'd love to invite you to a 30 minute video interview with the team next week.
                Please book a time that suits you using the link below.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.INTERVIEW);
        assertThat(finding.company()).isEqualTo("Canva");
        assertThat(finding.role()).isEqualTo("Graduate Software Engineer");
    }

    @Test
    void onlineAssessment() {
        var finding = read("Deloitte Graduate Recruitment", "graduates@deloitte.com.au",
                "Next step: online assessment",
                """
                Congratulations on progressing in your application with Deloitte.
                Please complete the online assessment on HackerRank within 5 days.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.ASSESSMENT);
        assertThat(finding.company()).isEqualTo("Deloitte");
    }

    @Test
    void offer() {
        var finding = read("NAB Careers", "careers@nab.com.au",
                "Letter of offer",
                """
                Dear Nithyashree,
                We are delighted to offer you the position of Data Engineer at NAB.
                Please find your letter of offer attached.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.OFFER);
        assertThat(finding.company()).isEqualTo("NAB");
    }

    @Test
    void jobAlertsAreIgnored() {
        assertThat(read("SEEK", "jobmail@s.seek.com.au",
                "12 new jobs for you",
                "New jobs matching your search: Graduate Developer. Apply now on SEEK.")).isEmpty();
        assertThat(read("LinkedIn", "jobalerts-noreply@linkedin.com",
                "Data Analyst: Canva is hiring",
                "Jobs you may be interested in. Canva is hiring a Data Analyst. Apply now.")).isEmpty();
    }

    @Test
    void everydayEmailsAreIgnored() {
        assertThat(read("Mum", "mum@gmail.com",
                "Flights booked",
                "We land in Melbourne on 12 December. Unfortunately the flight is very early.")).isEmpty();
    }

    @Test
    void seekConfirmationWhereTheRoleComesBeforeTheCompany() {
        var finding = read("SEEK", "noreply@s.seek.com.au",
                "Your application to ANZ",
                "Your application for Graduate Analyst was sent to ANZ.").orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.APPLIED);
        assertThat(finding.role()).isEqualTo("Graduate Analyst");
        assertThat(finding.company()).isEqualTo("ANZ");
    }

    @Test
    void rejectionThatTalksAboutWorkingWithTheCompany() {
        var finding = read("Kmart Careers", "no-reply@pageuppeople.com",
                "Application update",
                """
                Dear Nithyashree, thank you for your interest in working with Kmart.
                We have decided not to proceed with your application for the Retail Team Member position.
                """).orElseThrow();

        assertThat(finding.kind()).isEqualTo(JobEmailKind.REJECTED);
        assertThat(finding.company()).isEqualTo("Kmart");
        assertThat(finding.role()).isEqualTo("Retail Team Member");
    }

    @Test
    void shopEmailsThatSayUnfortunatelyAreIgnored() {
        assertThat(read("Coles", "no-reply@coles.com.au", "Weekly specials",
                "Unfortunately this offer ends soon.")).isEmpty();
    }

    @Test
    void companyComesFromTheSenderWhenTheTextDoesNotSayIt() {
        assertThat(JobEmailParser.companyFromSender("Canva Careers", "careers@canva.com", "canva.com"))
                .isEqualTo("Canva");
        assertThat(JobEmailParser.companyFromSender(null, "jobs@careers.atlassian.com", "careers.atlassian.com"))
                .isEqualTo("Atlassian");
        assertThat(JobEmailParser.companyFromSender(null, "telstra@myworkday.com", "myworkday.com"))
                .isEqualTo("Telstra");
        assertThat(JobEmailParser.companyFromSender("LinkedIn", "jobs-noreply@linkedin.com", "linkedin.com"))
                .isNull();
    }

    @Test
    void registeredNameHandlesAustralianDomains() {
        assertThat(JobEmailParser.registeredName("jobs.nab.com.au")).isEqualTo("nab");
        assertThat(JobEmailParser.registeredName("canva.com")).isEqualTo("canva");
    }
}
