ALTER TABLE app_users
    ADD COLUMN is_identity_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verification_document_type VARCHAR(30),
    ADD COLUMN masked_document_id VARCHAR(30),
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE;
