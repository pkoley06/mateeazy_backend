package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.chat.MessageType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * REST fallback body for POST /api/v1/chat/conversations/{conversationId}/messages.
 * <p>
 * A TEXT message needs {@code content}; an IMAGE message needs 1-5 {@code attachmentUrls}
 * (from {@code POST /api/v1/uploads/chat-images}); a GIF message needs exactly one. Content
 * presence is validated in {@code ChatService} because it depends on the type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String content;

    /** Defaults to TEXT when omitted (older clients). */
    private MessageType messageType;

    @Size(max = 5, message = "At most 5 attachments per message")
    private List<String> attachmentUrls;
}
