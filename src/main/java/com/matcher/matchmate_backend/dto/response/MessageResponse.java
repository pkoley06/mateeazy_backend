package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import com.matcher.matchmate_backend.model.enums.chat.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** One message, in both the paginated history and the live {@code /user/queue/messages} frame. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private UUID recipientId;
    /** Empty for an IMAGE / GIF message. */
    private String content;
    /** TEXT / IMAGE / GIF; older clients that ignore it still get {@code content}. */
    private MessageType messageType;
    /** 1-5 photo URLs for IMAGE, exactly one URL for GIF, empty for TEXT. */
    private List<String> attachmentUrls;
    private MessageStatus status;
    private OffsetDateTime sentAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime readAt;
}
