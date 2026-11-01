package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row in the chat inbox: {@code GET /api/v1/chat/conversations} and the create response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {
    private UUID id;
    private ConversationParticipantSummary participant;
    /** Null when the conversation wasn't started from a listing. */
    private ConversationListingSummary listing;
    /** Null for a brand-new conversation with no messages yet. */
    private LastMessageSummary lastMessage;
    /** The viewer's own unread count for this conversation. */
    private Integer unreadCount;
    private OffsetDateTime createdAt;
}
