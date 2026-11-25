-- V35: Reports on community content (posts, comments, 24h statuses).
--
-- A report keeps a snapshot of what was reported (text + media) so the admin
-- can still see it after the content is removed or a status expires. The
-- foreign keys are ON DELETE SET NULL for the same reason.

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS reported_post_id    UUID REFERENCES community_posts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reported_comment_id UUID REFERENCES community_comments(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reported_status_id  UUID REFERENCES user_statuses(id) ON DELETE SET NULL,
    -- The post a POST or COMMENT report belongs to (no FK, so the link survives deletion).
    ADD COLUMN IF NOT EXISTS context_post_id     UUID,
    ADD COLUMN IF NOT EXISTS content_preview     TEXT,
    ADD COLUMN IF NOT EXISTS content_media_url   VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_reports_reported_post_id ON reports(reported_post_id);
CREATE INDEX IF NOT EXISTS idx_reports_reported_comment_id ON reports(reported_comment_id);
CREATE INDEX IF NOT EXISTS idx_reports_reported_status_id ON reports(reported_status_id);
CREATE INDEX IF NOT EXISTS idx_reports_context_post_id ON reports(context_post_id);
