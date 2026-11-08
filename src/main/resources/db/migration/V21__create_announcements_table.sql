-- V21: Announcements & Festive Events broadcast table
CREATE TABLE IF NOT EXISTS announcements (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(250) NOT NULL,
    body             TEXT NOT NULL,
    image_url        VARCHAR(500),
    action_route     VARCHAR(255) DEFAULT '/discover',
    recipients_count INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_announcements_created_at
    ON announcements(created_at DESC);
