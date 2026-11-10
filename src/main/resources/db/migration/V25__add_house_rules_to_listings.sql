ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS house_rules text[] DEFAULT '{}';
