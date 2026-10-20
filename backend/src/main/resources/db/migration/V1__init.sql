-- Baseline migration for GradPilot.
-- pgcrypto provides gen_random_uuid() for primary keys in later migrations.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
