-- Accounts for people using GradPilot.
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Australia/Melbourne',
    wake_time     TIME,
    bed_time      TIME,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One account per email address, regardless of letter case.
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
