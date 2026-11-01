package com.matcher.matchmate_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** PATCH /api/v1/chat/conversations/{conversationId}/read. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadRequest {
    /** Null means "mark everything in the conversation as read". */
    private UUID lastReadMessageId;
}
