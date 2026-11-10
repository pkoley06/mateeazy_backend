-- V24: Chat requests (accept/reject), replies, message editing, 5-minute deletion window, and emoji reactions

-- 1. Conversation request status (PENDING, ACCEPTED, REJECTED) and initiator
ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS request_status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    ADD COLUMN IF NOT EXISTS initiator_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_conversations_request_status ON conversations(request_status);

-- 2. Message reply, edit, and deletion columns
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS reply_to_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reply_to_content TEXT,
    ADD COLUMN IF NOT EXISTS reply_to_sender_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_messages_reply_to_message_id ON messages(reply_to_message_id);

-- 3. Message emoji reactions table
CREATE TABLE IF NOT EXISTS message_reactions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    emoji      VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_user_reaction UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_message_reactions_message_id ON message_reactions(message_id);
