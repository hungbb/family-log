package app.giaky.platform.domain;

/** How the SMTP connection is protected. */
public enum MailSecurity {

    /** Plain text. Only for a relay on the same host or a local test server. */
    NONE,

    /** Connect in plain text, then upgrade with STARTTLS, which is required. Usually port 587. */
    STARTTLS,

    /** TLS from the first byte ("SMTPS"). Usually port 465. */
    SSL
}
