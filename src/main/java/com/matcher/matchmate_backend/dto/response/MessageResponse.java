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
    /** TEXT / IMAGE / GIF / AUDIO; older clients that ignore it still get {@code content}. */
    private MessageType messageType;
    /** 1-5 photo URLs for IMAGE, exactly one URL for GIF or AUDIO, empty for TEXT. */
    private List<String> attachmentUrls;
    /** AUDIO only: clip length in milliseconds. */
    private Integer durationMs;
    /** AUDIO only: loudness bars, one digit 0-9 each. */
    private String waveform;
    private MessageStatus status;
    private UUID replyToMessageId;
    private String replyToContent;
    private String replyToSenderName;
    private Boolean isEdited;
    private OffsetDateTime editedAt;
    private Boolean isDeleted;
    @Builder.Default
    private List<MessageReactionResponse> reactions = List.of();
    private OffsetDateTime sentAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime readAt;
}
