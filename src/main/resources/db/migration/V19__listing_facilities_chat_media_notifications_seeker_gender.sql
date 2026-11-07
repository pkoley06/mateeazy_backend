-- V19: Kitchen / washroom sharing on listings, image & GIF chat messages, a persisted
--      notification inbox, and the Room Finder Mode gender preference.

-- ------------------------------------------------------------------
-- 1. Listings: is the kitchen / washroom shared or private to this room?
--    Nullable: rooms listed before this migration simply don't say.
-- ------------------------------------------------------------------
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS kitchen_type  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS washroom_type VARCHAR(20);

-- ------------------------------------------------------------------
-- 2. Chat media: a message is TEXT (content), IMAGE (1-5 uploaded photos,
--    sent together so the app can draw them as one grouped bubble) or GIF
--    (one animated GIF URL). content stays NOT NULL; media messages store ''.
-- ------------------------------------------------------------------
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS message_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS attachment_urls TEXT[]      NOT NULL DEFAULT '{}';

-- The "5 photos a day" quota is counted off this index.
CREATE INDEX IF NOT EXISTS idx_messages_sender_type_sent_at
    ON messages(sender_id, message_type, sent_at);

-- ------------------------------------------------------------------
-- 3. Room Finder Mode gender preference: which rooms a seeker wants to see
--    (ALL / MALE / FEMALE / NON_BINARY / OTHER). NULL = the seeker's own gender.
-- ------------------------------------------------------------------
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS room_finder_gender VARCHAR(20);

-- ------------------------------------------------------------------
-- 4. Persisted notification inbox. Match and comment/like alerts are stored
--    here (in addition to the push) so the Notifications screen survives a
--    reinstall or a second device. Chat messages are NOT stored: their inbox
--    entry is derived from the conversation's unread count.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    type       VARCHAR(40) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL DEFAULT '',
    -- The FCM data payload as a JSON object (string -> string), so the app can
    -- deep-link a stored entry exactly like a tray tap.
    data_json  TEXT NOT NULL DEFAULT '{}',
    read_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_created
    ON user_notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_notifications_user_unread
    ON user_notifications(user_id) WHERE read_at IS NULL;
