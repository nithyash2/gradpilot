-- A private calendar address per person, for Google, Apple or Outlook Calendar to subscribe to.
-- Lookups use a hash of the secret token; the token itself is kept encrypted so the
-- address can be shown again on another device.
CREATE TABLE calendar_feeds (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
