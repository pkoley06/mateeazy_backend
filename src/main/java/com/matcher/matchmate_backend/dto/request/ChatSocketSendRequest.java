package com.matcher.matchmate_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** STOMP payload for {@code SEND /app/chat.send} - the conversation id travels in the body. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSocketSendRequest {
    private UUID conversationId;
    private String content;
}
