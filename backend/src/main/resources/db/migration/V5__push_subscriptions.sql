-- Browsers (or Home Screen apps on iPhone) that agreed to receive notifications.
CREATE TABLE push_subscriptions (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint        TEXT          NOT NULL,
    p256dh          VARCHAR(200)  NOT NULL,
    auth            VARCHAR(100)  NOT NULL,
    user_agent      VARCHAR(300),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    CONSTRAINT push_subscriptions_endpoint_key UNIQUE (endpoint)
);

CREATE INDEX push_subscriptions_user_idx ON push_subscriptions (user_id);

-- Remember which day the morning plan and evening wind-down were last sent,
-- so each goes out once a day.
ALTER TABLE users
    ADD COLUMN last_morning_sent DATE,
    ADD COLUMN last_evening_sent DATE;
