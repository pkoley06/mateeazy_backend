package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import com.matcher.matchmate_backend.model.enums.chat.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Preview of the most recent message in a conversation, for the chat inbox list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageSummary {
    private String content;
    /** TEXT / IMAGE / GIF so the list can show "Photo" / "GIF" instead of an empty line. */
    private MessageType messageType;
    /** How many attachments the message carries (so the preview can say "3 photos"). */
    private Integer attachmentCount;
    private OffsetDateTime sentAt;
    private MessageStatus status;
    private UUID senderId;
}
