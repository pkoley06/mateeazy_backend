package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
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
    private OffsetDateTime sentAt;
    private MessageStatus status;
    private UUID senderId;
}
