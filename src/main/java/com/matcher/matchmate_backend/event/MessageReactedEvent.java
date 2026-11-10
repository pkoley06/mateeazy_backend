package com.matcher.matchmate_backend.event;

import java.util.UUID;

public record MessageReactedEvent(
        UUID conversationId,
        UUID messageId,
        UUID reactorId,
        UUID authorId,
        String emoji,
        String messageContent
) {
}
