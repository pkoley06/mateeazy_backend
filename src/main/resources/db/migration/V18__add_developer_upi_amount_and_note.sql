-- "Buy me a coffee": the amount and the payment note handed to the UPI app are
-- set from the admin panel. Both are optional - a NULL amount leaves the amount
-- blank in the payment app (the payer types it), a NULL note uses the app default.
ALTER TABLE app_branding
    ADD COLUMN IF NOT EXISTS developer_upi_amount INTEGER,
    ADD COLUMN IF NOT EXISTS developer_upi_note VARCHAR(80);
