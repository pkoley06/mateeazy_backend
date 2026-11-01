package com.matcher.matchmate_backend.event;

import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published inside the {@code sendMessage} transaction; relayed after commit to the
 * recipient's {@code /user/queue/messages} and, if they aren't currently connected, to FCM.
 */
public record MessageSentEvent(
        UUID conversationId,
        UUID messageId,
        UUID senderId,
        UUID recipientId,
        String content,
        OffsetDateTime sentAt,
        MessageStatus status
) {
}
