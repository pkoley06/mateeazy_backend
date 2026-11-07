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

        // Also echoed back to the sender's own queue: this is the only acknowledgement a
        // socket-originated send gets back (STOMP's SEND has no built-in reply), and the
        // client relies on it both to resolve its optimistic local placeholder into the
        // persisted message's real id, and to receive later delivered/read status updates
        // against that id. Without this, a connected sender would never see an ack, assume
        // the send failed, and re-submit over REST - persisting the same message twice.
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
