-- Milestone 13: 30-day grace-period account deletion. Self-service deletion no longer purges
-- immediately; it schedules a purge, which a self-cancel within the window can undo.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scheduled_purge_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_app_users_scheduled_purge_at
    ON app_users(scheduled_purge_at)
    WHERE account_status = 'PENDING_DELETION';

-- Admin-configurable grace period length, added to the existing single-row app_branding table.
ALTER TABLE app_branding
    ADD COLUMN IF NOT EXISTS deletion_grace_period_days INTEGER NOT NULL DEFAULT 30;
