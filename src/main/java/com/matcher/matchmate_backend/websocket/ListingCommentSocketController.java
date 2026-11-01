package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.dto.request.ListingCommentCreateRequest;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ListingInteractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP entry point for posting a comment: {@code SEND /app/rooms/{roomId}/comment.send}
 * with a {@link ListingCommentCreateRequest} JSON body. Persists through the same service
 * as the REST endpoint, so the broadcast to {@code /topic/rooms/{roomId}/comments} and the
 * host push happen identically for both transports.
 */
@Controller
public class ListingCommentSocketController {

    private static final Logger log = LoggerFactory.getLogger(ListingCommentSocketController.class);
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final ListingInteractionService interactionService;

    public ListingCommentSocketController(ListingInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @MessageMapping("/rooms/{roomId}/comment.send")
    public void sendComment(
            @DestinationVariable UUID roomId,
            @Payload ListingCommentCreateRequest request,
            Principal principal
    ) {
        UUID userId = requireUserId(principal);
        validate(request);
        interactionService.addComment(userId, roomId, request);
    }

    /** Validation / authorization problems go back to the sender only, on {@code /user/queue/errors}. */
    @MessageExceptionHandler({IllegalArgumentException.class, AccessDeniedException.class, ResponseStatusException.class})
    @SendToUser("/queue/errors")
    public Map<String, String> handleClientError(Exception ex) {
        String message = ex instanceof ResponseStatusException rse && rse.getReason() != null
                ? rse.getReason()
                : ex.getMessage();
        log.debug("[WS] Rejected comment frame: {}", message);
        return Map.of("error", "COMMENT_REJECTED", "message", message == null ? "Rejected" : message);
    }

    private static UUID requireUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUserPrincipal user) {
            return user.getUserId();
        }
        throw new AccessDeniedException("Not authenticated");
    }

    /** Message-mapping payloads are not bean-validated by default; mirror the REST constraints. */
    private static void validate(ListingCommentCreateRequest request) {
        String content = request == null ? null : request.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Comment cannot be empty");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Comment cannot exceed " + MAX_CONTENT_LENGTH + " characters");
        }
    }
}
