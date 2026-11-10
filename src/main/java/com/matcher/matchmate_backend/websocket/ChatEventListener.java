package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.dto.response.MessageResponse;
import com.matcher.matchmate_backend.event.MessageSentEvent;
import com.matcher.matchmate_backend.event.MessagesDeliveredEvent;
import com.matcher.matchmate_backend.event.MessagesReadEvent;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Fans out chat side effects once the owning transaction has committed: STOMP delivery to
 * the recipient's/sender's own queue, and (for a new message) a push to the recipient when
 * they aren't currently connected (checked against the live {@link PresenceRegistry}, never
 * the persisted mirror, and against their notification preferences inside
 * {@link NotificationService}).
 */
@Component
public class ChatEventListener {

    private static final Logger log = LoggerFactory.getLogger(ChatEventListener.class);

    private final ChatMessageBroadcaster broadcaster;
    private final NotificationService notificationService;
    private final AppUserRepository userRepository;
    private final PresenceRegistry presenceRegistry;

    public ChatEventListener(
            ChatMessageBroadcaster broadcaster,
            NotificationService notificationService,
            AppUserRepository userRepository,
            PresenceRegistry presenceRegistry
    ) {
        this.broadcaster = broadcaster;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.presenceRegistry = presenceRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        MessageResponse payload = MessageResponse.builder()
                .id(event.messageId())
                .conversationId(event.conversationId())
                .senderId(event.senderId())
                .recipientId(event.recipientId())
                .content(event.content())
                .messageType(event.messageType())
                .attachmentUrls(event.attachmentUrls())
                .status(event.status())
                .replyToMessageId(event.replyToMessageId())
                .replyToContent(event.replyToContent())
                .replyToSenderName(event.replyToSenderName())
                .isEdited(false)
                .isDeleted(false)
                .reactions(java.util.List.of())
                .sentAt(event.sentAt())
                .deliveredAt(event.status() == MessageStatus.DELIVERED ? event.sentAt() : null)
                .readAt(null)
                .build();

        try {
            broadcaster.sendNewMessage(event.recipientId(), payload);
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast message {} to recipient {}: {}",
                    event.messageId(), event.recipientId(), e.getMessage());
        }

        try {
            broadcaster.sendNewMessage(event.senderId(), payload);
        } catch (Exception e) {
            log.warn("[WS] Failed to echo message {} to sender {}: {}",
                    event.messageId(), event.senderId(), e.getMessage());
        }

        AppUser recipient = userRepository.findById(event.recipientId()).orElse(null);
        AppUser sender = userRepository.findById(event.senderId()).orElse(null);
        if (recipient != null && sender != null) {
            notificationService.notifyRecipientOfChatMessage(
                    recipient, event.conversationId(), event.senderId(), sender.getFullName(),
                    sender.getAvatarUrl(), event.messageId(), event.previewText(), event.sentAt());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageUpdated(com.matcher.matchmate_backend.event.MessageUpdatedEvent event) {
        Map<String, Object> update = new java.util.HashMap<>();
        update.put("type", "MESSAGE_UPDATED");
        update.put("conversationId", event.conversationId());
        update.put("messageId", event.messageId());
        update.put("content", event.content());
        update.put("isEdited", event.isEdited());
        update.put("editedAt", event.editedAt() != null ? event.editedAt().toString() : null);
        update.put("isDeleted", event.isDeleted());
        update.put("deletedAt", event.deletedAt() != null ? event.deletedAt().toString() : null);
        update.put("reactions", event.reactions());

        try {
            broadcaster.sendStatusUpdate(event.participantOneId(), update);
        } catch (Exception e) {
            log.warn("[WS] Failed to send update to {}: {}", event.participantOneId(), e.getMessage());
        }
        try {
            broadcaster.sendStatusUpdate(event.participantTwoId(), update);
        } catch (Exception e) {
            log.warn("[WS] Failed to send update to {}: {}", event.participantTwoId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversationRequestChanged(com.matcher.matchmate_backend.event.ConversationRequestEvent event) {
        Map<String, Object> update = Map.of(
                "type", "REQUEST_STATUS_CHANGED",
                "conversationId", event.conversationId(),
                "requestStatus", event.status().name()
        );
        try {
            broadcaster.sendStatusUpdate(event.initiatorId(), update);
        } catch (Exception e) {
            log.warn("[WS] Failed to send request status to {}: {}", event.initiatorId(), e.getMessage());
        }
        try {
            broadcaster.sendStatusUpdate(event.recipientId(), update);
        } catch (Exception e) {
            log.warn("[WS] Failed to send request status to {}: {}", event.recipientId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagesDelivered(MessagesDeliveredEvent event) {
        try {
            // camelCase keys, matching MessageResponse and every other DTO in this app - the
            // presence topic is the one deliberate exception (snake_case, per the original spec).
            broadcaster.sendStatusUpdate(event.senderId(), Map.of(
                    "conversationId", event.conversationId(),
                    "messageIds", event.messageIds(),
                    "status", "DELIVERED",
                    "deliveredAt", event.deliveredAt()
            ));
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast delivery status for conversation {}: {}",
                    event.conversationId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagesRead(MessagesReadEvent event) {
        try {
            broadcaster.sendStatusUpdate(event.senderId(), Map.of(
                    "conversationId", event.conversationId(),
                    "messageIds", event.messageIds(),
                    "status", "READ",
                    "readAt", event.readAt()
            ));
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast read status for conversation {}: {}",
                    event.conversationId(), e.getMessage());
        }
    }
}
