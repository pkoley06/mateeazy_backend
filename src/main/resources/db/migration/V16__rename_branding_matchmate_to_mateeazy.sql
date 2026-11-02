-- App was rebranded from MatchMate to MateEazy; update column defaults for
-- future rows and the existing branding row (id = 1) seeded by V6/V10.
ALTER TABLE app_branding
    ALTER COLUMN app_name SET DEFAULT 'MateEazy',
    ALTER COLUMN support_email SET DEFAULT 'support@mateeazy.com',
    ALTER COLUMN developer_upi_id SET DEFAULT 'mateeazy.dev@upi',
    ALTER COLUMN developer_support_note SET DEFAULT 'Made with ❤️ for flatmates everywhere. If MateEazy helped you find a home, buy the developer a coffee!';

UPDATE app_branding
SET app_name = 'MateEazy',
    support_email = 'support@mateeazy.com',
    developer_upi_id = 'mateeazy.dev@upi',
    developer_support_note = 'Made with ❤️ for flatmates everywhere. If MateEazy helped you find a home, buy the developer a coffee!'
WHERE id = 1
  AND app_name = 'MatchMate';
