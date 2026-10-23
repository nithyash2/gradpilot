-- A person's connected mailbox (Outlook), so job emails are read automatically.
-- Tokens are encrypted by the API before they are stored.
CREATE TABLE mailbox_connections (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(20)  NOT NULL,
    email             VARCHAR(320),
    refresh_token     TEXT         NOT NULL,
    access_token      TEXT,
    access_expires_at TIMESTAMPTZ,
    needs_reconnect   BOOLEAN      NOT NULL DEFAULT FALSE,
    last_synced_at    TIMESTAMPTZ,
    last_received_at  TIMESTAMPTZ,
    last_error        VARCHAR(500),
    imported_count    INTEGER      NOT NULL DEFAULT 0,
    connected_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
