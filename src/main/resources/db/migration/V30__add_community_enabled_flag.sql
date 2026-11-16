-- V30: Admin kill-switch for the community / social feature.
ALTER TABLE app_branding
    ADD COLUMN IF NOT EXISTS community_enabled BOOLEAN NOT NULL DEFAULT TRUE;
