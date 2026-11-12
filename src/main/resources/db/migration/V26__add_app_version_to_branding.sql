-- V26: Add app version and in-app update fields to app_branding
ALTER TABLE app_branding
    ADD COLUMN IF NOT EXISTS latest_build_number INT NOT NULL DEFAULT 7,
    ADD COLUMN IF NOT EXISTS min_supported_build_number INT NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS release_notes TEXT DEFAULT 'New features, bug fixes, and performance improvements.',
    ADD COLUMN IF NOT EXISTS update_url VARCHAR(500) DEFAULT 'https://play.google.com/store/apps/details?id=com.mateeazy.app';

UPDATE app_branding
    SET latest_build_number = 7
    WHERE latest_build_number IS NULL;

UPDATE app_branding
    SET min_supported_build_number = 5
    WHERE min_supported_build_number IS NULL;
