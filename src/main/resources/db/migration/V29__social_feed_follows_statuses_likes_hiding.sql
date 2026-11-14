-- V29: Follows, Ephemeral Statuses / Stories, Hidden Posts, and Notification Toggles

-- 1. User Follows table (supports Direct Follow & Follow Requests)
CREATE TABLE IF NOT EXISTS user_follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED', -- 'PENDING' or 'ACCEPTED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_follows UNIQUE (follower_id, following_id)
);

CREATE INDEX IF NOT EXISTS idx_user_follows_follower ON user_follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_user_follows_following ON user_follows(following_id);

-- 2. User Statuses / Stories table (24-hour expiration)
CREATE TABLE IF NOT EXISTS user_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    media_url VARCHAR(1024),
    caption TEXT,
    background_color VARCHAR(16),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX IF NOT EXISTS idx_user_statuses_user_expires ON user_statuses(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_user_statuses_expires ON user_statuses(expires_at);

-- 3. User Status Views tracking
CREATE TABLE IF NOT EXISTS user_status_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status_id UUID NOT NULL REFERENCES user_statuses(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_status_view UNIQUE (status_id, viewer_id)
);

CREATE INDEX IF NOT EXISTS idx_user_status_views_status ON user_status_views(status_id);

-- 4. User Hidden Posts ("Not interested")
CREATE TABLE IF NOT EXISTS user_hidden_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_hidden_post UNIQUE (user_id, post_id)
);

CREATE INDEX IF NOT EXISTS idx_user_hidden_posts_user ON user_hidden_posts(user_id);

-- 5. Extend user notification preferences for followers & status reactions
ALTER TABLE user_notification_preferences
    ADD COLUMN IF NOT EXISTS notify_followers BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_status_reactions BOOLEAN NOT NULL DEFAULT TRUE;

-- 6. Privacy toggle for direct follow vs follow request approval
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS direct_follow_enabled BOOLEAN NOT NULL DEFAULT TRUE;
