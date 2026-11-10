package com.matcher.matchmate_backend.event;

import com.matcher.matchmate_backend.dto.response.MessageReactionResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MessageUpdatedEvent(
        UUID conversationId,
        UUID messageId,
        UUID participantOneId,
        UUID participantTwoId,
        String content,
        Boolean isEdited,
        OffsetDateTime editedAt,
        Boolean isDeleted,
        OffsetDateTime deletedAt,
        List<MessageReactionResponse> reactions
) {
}
