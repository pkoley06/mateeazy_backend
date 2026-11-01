package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.dto.response.MessageResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Single place that knows the per-user chat queue layout: {@code /user/queue/messages(/status)}. */
@Component
public class ChatMessageBroadcaster {

    public static final String MESSAGES_QUEUE = "/queue/messages";
    public static final String MESSAGE_STATUS_QUEUE = "/queue/messages/status";

    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** A new message, delivered to the recipient's own queue. */
    public void sendNewMessage(UUID recipientId, MessageResponse payload) {
        messagingTemplate.convertAndSendToUser(recipientId.toString(), MESSAGES_QUEUE, payload);
    }

    /** A delivered/read status update, delivered to the original sender's own queue. */
    public void sendStatusUpdate(UUID userId, Object payload) {
        messagingTemplate.convertAndSendToUser(userId.toString(), MESSAGE_STATUS_QUEUE, payload);
    }
}
