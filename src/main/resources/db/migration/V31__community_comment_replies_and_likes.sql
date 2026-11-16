-- V31: Threaded replies (up to three levels) and likes on community post comments.

ALTER TABLE community_comments
    ADD COLUMN parent_id UUID REFERENCES community_comments(id) ON DELETE CASCADE,
    ADD COLUMN depth SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX idx_community_comments_parent_id ON community_comments(parent_id);

CREATE TABLE community_comment_likes (
    comment_id UUID NOT NULL REFERENCES community_comments(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX idx_community_comment_likes_user_id ON community_comment_likes(user_id);
