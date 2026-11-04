-- Room Finder Mode is opt-in: new accounts start with it OFF.
-- Only the column default changes; existing users keep whatever they have now.
ALTER TABLE app_users
    ALTER COLUMN is_looking_for_room SET DEFAULT FALSE;
