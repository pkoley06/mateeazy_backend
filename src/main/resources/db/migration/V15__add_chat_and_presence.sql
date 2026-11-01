-- V15: Chat (conversations + messages) and presence tracking, on top of the UUID ids
-- established in V14. Conversation/Message are brand new, so they get UUID primary keys
-- from inception (gen_random_uuid(), pgcrypto extension ensured).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------------------------------------
-- 1. Presence columns on app_users
-- ------------------------------------------------------------------------------------------
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS is_online BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE;

-- ------------------------------------------------------------------------------------------
-- 2. One 1-to-1 conversation per unordered pair of users, optionally linked to a listing.
--    user_one_id / user_two_id are always stored with user_one_id < user_two_id so the pair
--    is unique regardless of who started the conversation, and look-up is a single query.
-- ------------------------------------------------------------------------------------------
CREATE TABLE conversations (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_one_id            UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    user_two_id            UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    listing_id             UUID REFERENCES listings(id) ON DELETE SET NULL,
    last_message_at        TIMESTAMP WITH TIME ZONE,
    unread_count_user_one  INTEGER NOT NULL DEFAULT 0,
    unread_count_user_two  INTEGER NOT NULL DEFAULT 0,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversations_pair UNIQUE (user_one_id, user_two_id),
    CONSTRAINT chk_conversations_pair_order CHECK (user_one_id < user_two_id)
);

CREATE INDEX idx_conversations_user_one ON conversations(user_one_id);
CREATE INDEX idx_conversations_user_two ON conversations(user_two_id);
CREATE INDEX idx_conversations_listing ON conversations(listing_id);

-- ------------------------------------------------------------------------------------------
-- 3. Messages within a conversation, with SENT -> DELIVERED -> READ lifecycle timestamps.
-- ------------------------------------------------------------------------------------------
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    recipient_id    UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SENT',
    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at    TIMESTAMP WITH TIME ZONE,
    read_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_messages_conversation_sent_at ON messages(conversation_id, sent_at);
CREATE INDEX idx_messages_recipient_status ON messages(recipient_id, status);
