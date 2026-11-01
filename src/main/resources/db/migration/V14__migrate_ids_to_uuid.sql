-- V14: Platform-wide migration off sequential BIGINT ids onto non-sequential UUID v4 ids,
-- for every entity the chat-system engineering spec names (User, Room, Comment — Conversation
-- and Message are brand new and get UUID ids from inception in V15). AdminUser and AppBranding
-- are internal/admin-only and are intentionally left on BIGINT IDENTITY — out of scope.
--
-- Data-preserving, not a wipe: every existing row keeps its data and relationships, it just
-- gets a new random primary key.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
--
-- Strategy per table: add a new UUID column (backfilled per-row), add new UUID FK columns and
-- populate them by joining on the OLD bigint id to the parent's freshly-assigned new id, then
-- drop every old bigint id/FK column with CASCADE (which also drops the constraints and
-- indexes defined on them, including cross-table foreign keys), rename the new columns into
-- the original names, and finally re-create every primary key, foreign key, and secondary
-- index that existed before.

-- ============================================================================================
-- STEP 1: add + backfill new UUID columns (parents before children, so joins have a source)
-- ============================================================================================

-- app_users (root)
ALTER TABLE app_users ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();

-- listings (depends on app_users)
ALTER TABLE listings ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE listings ADD COLUMN new_host_user_id UUID;
UPDATE listings l SET new_host_user_id = u.new_id FROM app_users u WHERE u.id = l.host_user_id;
ALTER TABLE listings ALTER COLUMN new_host_user_id SET NOT NULL;

-- listing_likes (depends on app_users, listings)
ALTER TABLE listing_likes ADD COLUMN new_user_id UUID;
ALTER TABLE listing_likes ADD COLUMN new_listing_id UUID;
UPDATE listing_likes ll SET new_user_id = u.new_id FROM app_users u WHERE u.id = ll.user_id;
UPDATE listing_likes ll SET new_listing_id = l.new_id FROM listings l WHERE l.id = ll.listing_id;
ALTER TABLE listing_likes ALTER COLUMN new_user_id SET NOT NULL;
ALTER TABLE listing_likes ALTER COLUMN new_listing_id SET NOT NULL;

-- listing_comments (depends on app_users, listings)
ALTER TABLE listing_comments ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE listing_comments ADD COLUMN new_listing_id UUID;
ALTER TABLE listing_comments ADD COLUMN new_user_id UUID;
UPDATE listing_comments lc SET new_listing_id = l.new_id FROM listings l WHERE l.id = lc.listing_id;
UPDATE listing_comments lc SET new_user_id = u.new_id FROM app_users u WHERE u.id = lc.user_id;
ALTER TABLE listing_comments ALTER COLUMN new_listing_id SET NOT NULL;
ALTER TABLE listing_comments ALTER COLUMN new_user_id SET NOT NULL;

-- listing_comment_likes (depends on listing_comments, app_users)
ALTER TABLE listing_comment_likes ADD COLUMN new_comment_id UUID;
ALTER TABLE listing_comment_likes ADD COLUMN new_user_id UUID;
UPDATE listing_comment_likes lcl SET new_comment_id = lc.new_id
    FROM listing_comments lc WHERE lc.id = lcl.comment_id;
UPDATE listing_comment_likes lcl SET new_user_id = u.new_id FROM app_users u WHERE u.id = lcl.user_id;
ALTER TABLE listing_comment_likes ALTER COLUMN new_comment_id SET NOT NULL;
ALTER TABLE listing_comment_likes ALTER COLUMN new_user_id SET NOT NULL;

-- reports (depends on app_users x2, listings; reported_user_id/reported_listing_id are nullable)
ALTER TABLE reports ADD COLUMN new_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE reports ADD COLUMN new_reporter_id UUID;
ALTER TABLE reports ADD COLUMN new_reported_user_id UUID;
ALTER TABLE reports ADD COLUMN new_reported_listing_id UUID;
UPDATE reports r SET new_reporter_id = u.new_id FROM app_users u WHERE u.id = r.reporter_id;
UPDATE reports r SET new_reported_user_id = u.new_id
    FROM app_users u WHERE u.id = r.reported_user_id AND r.reported_user_id IS NOT NULL;
UPDATE reports r SET new_reported_listing_id = l.new_id
    FROM listings l WHERE l.id = r.reported_listing_id AND r.reported_listing_id IS NOT NULL;
ALTER TABLE reports ALTER COLUMN new_reporter_id SET NOT NULL;

-- user_blocks (depends on app_users x2)
ALTER TABLE user_blocks ADD COLUMN new_blocker_id UUID;
ALTER TABLE user_blocks ADD COLUMN new_blocked_id UUID;
UPDATE user_blocks ub SET new_blocker_id = u.new_id FROM app_users u WHERE u.id = ub.blocker_id;
UPDATE user_blocks ub SET new_blocked_id = u.new_id FROM app_users u WHERE u.id = ub.blocked_id;
ALTER TABLE user_blocks ALTER COLUMN new_blocker_id SET NOT NULL;
ALTER TABLE user_blocks ALTER COLUMN new_blocked_id SET NOT NULL;

-- user_notification_preferences (PK = FK to app_users)
ALTER TABLE user_notification_preferences ADD COLUMN new_user_id UUID;
UPDATE user_notification_preferences unp SET new_user_id = u.new_id
    FROM app_users u WHERE u.id = unp.user_id;
ALTER TABLE user_notification_preferences ALTER COLUMN new_user_id SET NOT NULL;

-- ============================================================================================
-- STEP 2: drop every old bigint id/FK column with CASCADE (removes old PKs, old cross-table
-- FKs, and the plain indexes defined on them in one shot)
-- ============================================================================================

ALTER TABLE listing_comment_likes DROP CONSTRAINT listing_comment_likes_pkey;
ALTER TABLE listing_comment_likes DROP COLUMN comment_id CASCADE;
ALTER TABLE listing_comment_likes DROP COLUMN user_id CASCADE;

ALTER TABLE listing_likes DROP CONSTRAINT listing_likes_pkey;
ALTER TABLE listing_likes DROP COLUMN user_id CASCADE;
ALTER TABLE listing_likes DROP COLUMN listing_id CASCADE;

ALTER TABLE user_blocks DROP CONSTRAINT user_blocks_pkey;
ALTER TABLE user_blocks DROP COLUMN blocker_id CASCADE;
ALTER TABLE user_blocks DROP COLUMN blocked_id CASCADE;

ALTER TABLE user_notification_preferences DROP CONSTRAINT user_notification_preferences_pkey;
ALTER TABLE user_notification_preferences DROP COLUMN user_id CASCADE;

ALTER TABLE reports DROP COLUMN reported_listing_id CASCADE;
ALTER TABLE reports DROP COLUMN reported_user_id CASCADE;
ALTER TABLE reports DROP COLUMN reporter_id CASCADE;
ALTER TABLE reports DROP COLUMN id CASCADE;

ALTER TABLE listing_comments DROP COLUMN listing_id CASCADE;
ALTER TABLE listing_comments DROP COLUMN user_id CASCADE;
ALTER TABLE listing_comments DROP COLUMN id CASCADE;

ALTER TABLE listings DROP COLUMN host_user_id CASCADE;
ALTER TABLE listings DROP COLUMN id CASCADE;

ALTER TABLE app_users DROP COLUMN id CASCADE;

-- ============================================================================================
-- STEP 3: rename the new columns into their final names
-- ============================================================================================

ALTER TABLE app_users RENAME COLUMN new_id TO id;

ALTER TABLE listings RENAME COLUMN new_id TO id;
ALTER TABLE listings RENAME COLUMN new_host_user_id TO host_user_id;

ALTER TABLE listing_likes RENAME COLUMN new_user_id TO user_id;
ALTER TABLE listing_likes RENAME COLUMN new_listing_id TO listing_id;

ALTER TABLE listing_comments RENAME COLUMN new_id TO id;
ALTER TABLE listing_comments RENAME COLUMN new_listing_id TO listing_id;
ALTER TABLE listing_comments RENAME COLUMN new_user_id TO user_id;

ALTER TABLE listing_comment_likes RENAME COLUMN new_comment_id TO comment_id;
ALTER TABLE listing_comment_likes RENAME COLUMN new_user_id TO user_id;

ALTER TABLE reports RENAME COLUMN new_id TO id;
ALTER TABLE reports RENAME COLUMN new_reporter_id TO reporter_id;
ALTER TABLE reports RENAME COLUMN new_reported_user_id TO reported_user_id;
ALTER TABLE reports RENAME COLUMN new_reported_listing_id TO reported_listing_id;

ALTER TABLE user_blocks RENAME COLUMN new_blocker_id TO blocker_id;
ALTER TABLE user_blocks RENAME COLUMN new_blocked_id TO blocked_id;

ALTER TABLE user_notification_preferences RENAME COLUMN new_user_id TO user_id;

-- ============================================================================================
-- STEP 4: re-create primary keys (parents before the foreign keys that reference them)
-- ============================================================================================

ALTER TABLE app_users ADD PRIMARY KEY (id);
ALTER TABLE listings ADD PRIMARY KEY (id);
ALTER TABLE listing_comments ADD PRIMARY KEY (id);
ALTER TABLE reports ADD PRIMARY KEY (id);
ALTER TABLE listing_likes ADD PRIMARY KEY (user_id, listing_id);
ALTER TABLE user_blocks ADD PRIMARY KEY (blocker_id, blocked_id);
ALTER TABLE listing_comment_likes ADD PRIMARY KEY (comment_id, user_id);
ALTER TABLE user_notification_preferences ADD PRIMARY KEY (user_id);

-- ============================================================================================
-- STEP 5: re-create foreign keys (same ON DELETE semantics as the original schema)
-- ============================================================================================

ALTER TABLE listings
    ADD CONSTRAINT listings_host_user_id_fkey
    FOREIGN KEY (host_user_id) REFERENCES app_users(id) ON DELETE CASCADE;

ALTER TABLE listing_likes
    ADD CONSTRAINT listing_likes_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    ADD CONSTRAINT listing_likes_listing_id_fkey
    FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE;

ALTER TABLE listing_comments
    ADD CONSTRAINT listing_comments_listing_id_fkey
    FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    ADD CONSTRAINT listing_comments_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE;

ALTER TABLE listing_comment_likes
    ADD CONSTRAINT listing_comment_likes_comment_id_fkey
    FOREIGN KEY (comment_id) REFERENCES listing_comments(id) ON DELETE CASCADE,
    ADD CONSTRAINT listing_comment_likes_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE;

ALTER TABLE reports
    ADD CONSTRAINT reports_reporter_id_fkey
    FOREIGN KEY (reporter_id) REFERENCES app_users(id) ON DELETE CASCADE,
    ADD CONSTRAINT reports_reported_user_id_fkey
    FOREIGN KEY (reported_user_id) REFERENCES app_users(id) ON DELETE CASCADE,
    ADD CONSTRAINT reports_reported_listing_id_fkey
    FOREIGN KEY (reported_listing_id) REFERENCES listings(id) ON DELETE CASCADE;

ALTER TABLE user_blocks
    ADD CONSTRAINT user_blocks_blocker_id_fkey
    FOREIGN KEY (blocker_id) REFERENCES app_users(id) ON DELETE CASCADE,
    ADD CONSTRAINT user_blocks_blocked_id_fkey
    FOREIGN KEY (blocked_id) REFERENCES app_users(id) ON DELETE CASCADE;

ALTER TABLE user_notification_preferences
    ADD CONSTRAINT user_notification_preferences_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE;

-- ============================================================================================
-- STEP 6: re-create secondary indexes that were defined on the dropped bigint columns
-- (primary-key-backing indexes were already re-created implicitly by STEP 4)
-- ============================================================================================

CREATE INDEX idx_listings_host_id ON listings(host_user_id);
CREATE INDEX idx_listing_likes_listing_id ON listing_likes(listing_id);
CREATE INDEX idx_listing_likes_user_id ON listing_likes(user_id);
CREATE INDEX idx_listing_comments_listing_id ON listing_comments(listing_id);
CREATE INDEX idx_listing_comments_user_id ON listing_comments(user_id);
CREATE INDEX idx_user_blocks_blocker_id ON user_blocks(blocker_id);
CREATE INDEX idx_user_blocks_blocked_id ON user_blocks(blocked_id);
CREATE INDEX idx_listing_comment_likes_user_id ON listing_comment_likes(user_id);
