-- "Delete chat" is per person: it hides the conversation from that user's list and hides
-- every message sent up to that moment from them, while the other participant keeps the
-- whole history. A later message brings the chat back for the user, showing only what was
-- sent after they deleted it. NULL = never deleted.
ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS cleared_at_user_one TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cleared_at_user_two TIMESTAMPTZ;
