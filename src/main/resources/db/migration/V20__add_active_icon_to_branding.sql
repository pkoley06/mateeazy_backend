-- V20: Add active_icon for dynamic runtime launcher icon switching (e.g. default, diwali)
ALTER TABLE app_branding
    ADD COLUMN IF NOT EXISTS active_icon VARCHAR(50) DEFAULT 'default';

UPDATE app_branding
    SET active_icon = 'default'
    WHERE active_icon IS NULL;
