package com.matcher.matchmate_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** STOMP payload for {@code SEND /app/chat.read}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSocketReadRequest {
    private UUID conversationId;
    private UUID lastReadMessageId;
}
