package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.dto.request.ChatSocketReadRequest;
import com.matcher.matchmate_backend.dto.request.ChatSocketSendRequest;
import com.matcher.matchmate_backend.dto.request.ChatSocketTypingRequest;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP entry points for chat: {@code SEND /app/chat.send} and {@code SEND /app/chat.read}.
 * Both persist through the same {@link ChatService} the REST fallback uses, so the
 * recipient's {@code /user/queue/messages} delivery and FCM push happen identically for
 * either transport.
 */
@Controller
public class ChatSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketController.class);

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatSocketSendRequest request, Principal principal) {
        UUID userId = requireUserId(principal);
        chatService.sendMessage(request.getConversationId(), userId, request.getContent(),
                request.getMessageType(), request.getAttachmentUrls(), request.getReplyToMessageId());
    }

    @MessageMapping("/chat.read")
    public void markRead(@Payload ChatSocketReadRequest request, Principal principal) {
        UUID userId = requireUserId(principal);
        chatService.markRead(request.getConversationId(), userId, request.getLastReadMessageId());
    }

    /**
     * Ephemeral "is typing" signal, relayed to the other participant only. Nothing is
     * stored; a stale or invalid frame is dropped silently rather than answered.
     */
    @MessageMapping("/chat.typing")
    public void typing(@Payload ChatSocketTypingRequest request, Principal principal) {
        if (request == null || request.getConversationId() == null) return;
        UUID userId = requireUserId(principal);
        chatService.typingRecipient(request.getConversationId(), userId).ifPresent(recipient ->
                messagingTemplate.convertAndSendToUser(recipient.toString(), "/queue/typing", Map.of(
                        "conversationId", request.getConversationId().toString(),
                        "userId", userId.toString(),
                        "typing", request.isTyping())));
    }

    /** Validation / authorization problems go back to the sender only, on {@code /user/queue/errors}. */
    @MessageExceptionHandler({IllegalArgumentException.class, AccessDeniedException.class, ResponseStatusException.class})
    @SendToUser("/queue/errors")
    public Map<String, String> handleClientError(Exception ex) {
        String message = ex instanceof ResponseStatusException rse && rse.getReason() != null
                ? rse.getReason()
                : ex.getMessage();
        log.debug("[WS] Rejected chat frame: {}", message);
        return Map.of("error", "CHAT_REJECTED", "message", message == null ? "Rejected" : message);
    }

    private static UUID requireUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUserPrincipal user) {
            return user.getUserId();
        }
        throw new AccessDeniedException("Not authenticated");
    }
}
