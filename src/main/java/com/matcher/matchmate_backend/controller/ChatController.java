package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.request.MarkReadRequest;
import com.matcher.matchmate_backend.dto.request.SendMessageRequest;
import com.matcher.matchmate_backend.dto.request.StartConversationRequest;
import com.matcher.matchmate_backend.dto.response.ChatMediaQuotaResponse;
import com.matcher.matchmate_backend.dto.response.ConversationSummaryResponse;
import com.matcher.matchmate_backend.dto.response.MessageResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ChatMediaQuotaService;
import com.matcher.matchmate_backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatMediaQuotaService mediaQuotaService;

    public ChatController(ChatService chatService, ChatMediaQuotaService mediaQuotaService) {
        this.chatService = chatService;
        this.mediaQuotaService = mediaQuotaService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryResponse>> listConversations(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(chatService.listConversations(principal.getUserId()));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationSummaryResponse> startConversation(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody StartConversationRequest request
    ) {
        return ResponseEntity.ok(chatService.getOrCreateConversation(
                principal.getUserId(), request.getOtherUserId(), request.getListingId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<PageResponse<MessageResponse>> getMessages(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        return ResponseEntity.ok(chatService.getMessages(conversationId, principal.getUserId(), pageable));
    }

    /** REST fallback for sending; the STOMP path ({@code /app/chat.send}) is equivalent. */
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(conversationId, principal.getUserId(), request.getContent(),
                        request.getMessageType(), request.getAttachmentUrls()));
    }

    /** How many photos the user may still send today (the composer greys out the picker at 0). */
    @GetMapping("/media-quota")
    public ResponseEntity<ChatMediaQuotaResponse> mediaQuota(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(mediaQuotaService.quotaFor(principal.getUserId()));
    }

    @PatchMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody(required = false) MarkReadRequest request
    ) {
        UUID lastReadMessageId = request != null ? request.getLastReadMessageId() : null;
        chatService.markRead(conversationId, principal.getUserId(), lastReadMessageId);
        return ResponseEntity.noContent().build();
    }
}
