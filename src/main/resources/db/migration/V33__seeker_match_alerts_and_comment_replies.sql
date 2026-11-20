-- 1) Ledger of "a room seeker is looking near your room" alerts already sent, one row per
--    (host, seeker). Turning Room Finder Mode on hits two endpoints back to back (room-seeker
--    toggle + alert area save) and a host may own several listings, which used to alert the
--    same host about the same seeker more than once. NotificationService now claims the row
--    atomically before alerting, so it goes out once per Room Finder session.
CREATE TABLE IF NOT EXISTS seeker_match_alerts (
    host_user_id   UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    seeker_user_id UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    notified_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (host_user_id, seeker_user_id)
);

CREATE INDEX IF NOT EXISTS idx_seeker_match_alerts_seeker ON seeker_match_alerts (seeker_user_id);

-- 2) New switch: replies on rooms you commented on (non-host participants of a thread).
ALTER TABLE user_notification_preferences
    ADD COLUMN IF NOT EXISTS notify_comment_replies BOOLEAN NOT NULL DEFAULT TRUE;
