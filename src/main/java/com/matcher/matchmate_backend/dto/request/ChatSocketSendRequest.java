package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.chat.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** STOMP payload for {@code SEND /app/chat.send} - the conversation id travels in the body. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSocketSendRequest {
    private UUID conversationId;
    private String content;
    /** Defaults to TEXT when omitted. */
    private MessageType messageType;
    private List<String> attachmentUrls;
    private UUID replyToMessageId;
}
