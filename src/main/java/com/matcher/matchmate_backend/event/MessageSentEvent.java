package com.matcher.matchmate_backend.event;

import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import com.matcher.matchmate_backend.model.enums.chat.MessageType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Published inside the {@code sendMessage} transaction; relayed after commit to the
 * recipient's {@code /user/queue/messages} and, if they aren't currently connected, to FCM.
 */
public record MessageSentEvent(
        UUID conversationId,
        UUID messageId,
        UUID senderId,
        UUID recipientId,
        String content,
        MessageType messageType,
        List<String> attachmentUrls,
        OffsetDateTime sentAt,
        MessageStatus status,
        UUID replyToMessageId,
        String replyToContent,
        String replyToSenderName
) {
    /** What a notification / list preview shows for this message. */
    public String previewText() {
        return previewText(messageType, content, attachmentUrls == null ? 0 : attachmentUrls.size());
    }

    public static String previewText(MessageType type, String content, int attachmentCount) {
        if (type == null || type == MessageType.TEXT) {
            if (content == null) return "";
            // A reply to a status carries a "[[status:id]]" marker line; show only the words.
            String stripped = content.replaceFirst("^\\[\\[status:[0-9a-fA-F-]+\\]\\]\\R?", "");
            return stripped.length() == content.length() ? content : "Replied to a status: " + stripped;
        }
        if (type == MessageType.GIF) return "GIF";
        return attachmentCount > 1 ? "📷 " + attachmentCount + " photos" : "📷 Photo";
    }
}
