package com.matcher.matchmate_backend.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A batch of {@code senderId}'s messages were just marked DELIVERED because the recipient reconnected. */
public record MessagesDeliveredEvent(
        UUID conversationId,
        UUID senderId,
        List<UUID> messageIds,
        OffsetDateTime deliveredAt
) {
}
