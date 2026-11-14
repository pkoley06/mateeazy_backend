-- V28: Add optional tagged listing reference to community posts
ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS tagged_listing_id UUID REFERENCES listings(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_community_posts_tagged_listing ON community_posts(tagged_listing_id);
