-- V22: the identifier each user signed in with (Google email / phone number), captured at login.
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
