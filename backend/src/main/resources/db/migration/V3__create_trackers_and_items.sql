-- A tracker is anything a person wants to follow: a checklist, a job pipeline,
-- course progress, a log, a countdown or a work schedule.
CREATE TABLE trackers (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL
        CHECK (type IN ('CHECKLIST', 'PIPELINE', 'PROGRESS', 'LOG', 'COUNTDOWN', 'SCHEDULE')),
    template    VARCHAR(40),
    colour      VARCHAR(20),
    -- Type-specific options, e.g. pipeline stages or travel time to work
    settings    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX trackers_user_idx ON trackers (user_id, sort_order);

-- One flexible table serves every tracker type. Columns cover the common
-- fields; anything else goes in the fields JSONB column.
CREATE TABLE items (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tracker_id       UUID          NOT NULL REFERENCES trackers (id) ON DELETE CASCADE,
    title            VARCHAR(200)  NOT NULL,
    notes            TEXT,
    done             BOOLEAN       NOT NULL DEFAULT false,
    stage            VARCHAR(50),
    progress         INTEGER       CHECK (progress BETWEEN 0 AND 100),
    amount           NUMERIC(10, 2),
    item_date        DATE,
    start_time       TIME,
    end_time         TIME,
    reminder_at      TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,
    fields           JSONB         NOT NULL DEFAULT '{}'::jsonb,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX items_tracker_idx ON items (tracker_id, sort_order);
CREATE INDEX items_date_idx ON items (item_date);
CREATE INDEX items_pending_reminder_idx ON items (reminder_at) WHERE reminder_sent_at IS NULL;
