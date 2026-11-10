package com.matcher.matchmate_backend.event;

import com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus;

import java.util.UUID;

public record ConversationRequestEvent(
        UUID conversationId,
        UUID initiatorId,
        UUID recipientId,
        ConversationRequestStatus status
) {
}
