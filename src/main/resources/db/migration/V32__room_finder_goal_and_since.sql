-- Room Finder Mode can now seek either a room or a flatmate ("mate"), and the
-- Discover card shows how long ago the person started looking.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS room_finder_goal VARCHAR(10) NOT NULL DEFAULT 'ROOM',
    ADD COLUMN IF NOT EXISTS room_finder_since TIMESTAMPTZ;

-- Existing finders: treat "now" as the start so cards never show a blank age.
UPDATE app_users
   SET room_finder_since = NOW()
 WHERE is_looking_for_room = TRUE
   AND room_finder_since IS NULL;
