-- When each person wants their morning plan and evening wind-down.
ALTER TABLE users
    ADD COLUMN morning_time TIME NOT NULL DEFAULT '07:10',
    ADD COLUMN evening_time TIME NOT NULL DEFAULT '21:40';

-- One daily check-in per person per day.
CREATE TABLE checkins (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day         DATE          NOT NULL,
    wake_time   TIME,
    breakfast   VARCHAR(200),
    lunch       VARCHAR(200),
    dinner      VARCHAR(200),
    self_care   BOOLEAN       NOT NULL DEFAULT false,
    dishes      BOOLEAN       NOT NULL DEFAULT false,
    bed_on_time BOOLEAN       NOT NULL DEFAULT false,
    focus       VARCHAR(200),
    focus_done  BOOLEAN       NOT NULL DEFAULT false,
    notes       TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT checkins_user_day_key UNIQUE (user_id, day)
);
