package com.matcher.matchmate_backend.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A batch of {@code senderId}'s messages in this conversation were just marked READ by the other participant. */
public record MessagesReadEvent(
        UUID conversationId,
        UUID senderId,
        List<UUID> messageIds,
        OffsetDateTime readAt
) {
}
