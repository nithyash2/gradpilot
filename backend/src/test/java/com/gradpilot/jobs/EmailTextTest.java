package com.gradpilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTextTest {

    @Test
    void readsHeaderLinesFromAPastedEmail() {
        JobEmail email = EmailText.pasted(null, null, """
                From: Canva Careers <careers@canva.com>
                Sent: Friday, 23 October 2026 4:12 PM
                To: kp.nithyashree@outlook.com
                Subject: Thanks for applying

                Hi Nithyashree,
                Thanks for applying.
                """, null);

        assertThat(email.fromName()).isEqualTo("Canva Careers");
        assertThat(email.fromAddress()).isEqualTo("careers@canva.com");
        assertThat(email.subject()).isEqualTo("Thanks for applying");
        assertThat(email.text()).startsWith("Hi Nithyashree").doesNotContain("Subject:");
        assertThat(email.messageId()).startsWith("pasted:");
    }

    @Test
    void usesTheFirstLineAsTheSubjectWhenThereAreNoHeaders() {
        JobEmail email = EmailText.pasted(null, null, "Your application was sent to Canva\nGraduate Engineer", null);

        assertThat(email.subject()).isEqualTo("Your application was sent to Canva");
        assertThat(email.fromAddress()).isNull();
    }

    @Test
    void samePastedEmailGetsTheSameId() {
        String text = "Subject: Hello\n\nThanks for applying.";
        assertThat(EmailText.pasted(null, null, text, null).messageId())
                .isEqualTo(EmailText.pasted(null, null, text, null).messageId());
    }

    @Test
    void readsSendersInEveryUsualShape() {
        assertThat(EmailText.sender("\"Canva Careers\" <careers@canva.com>"))
                .isEqualTo(new EmailText.Sender("Canva Careers", "careers@canva.com"));
        assertThat(EmailText.sender("careers@canva.com"))
                .isEqualTo(new EmailText.Sender(null, "careers@canva.com"));
        assertThat(EmailText.sender("Canva Careers"))
                .isEqualTo(new EmailText.Sender("Canva Careers", null));
    }

    @Test
    void turnsHtmlIntoPlainText() {
        String text = EmailText.fromHtml("""
                <html><head><style>p{color:red}</style></head><body>
                <p>Hi&nbsp;Nithya,</p><p>Thanks for applying to <b>Canva</b>&#33;</p>
                <div>We&rsquo;ll be in touch.<br>Team</div></body></html>
                """);

        assertThat(text).isEqualTo("Hi Nithya,\nThanks for applying to Canva!\nWe'll be in touch.\nTeam");
    }
}
