package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.request.ListingCommentCreateRequest;
import com.matcher.matchmate_backend.dto.response.ListingCommentLikeStatusResponse;
import com.matcher.matchmate_backend.dto.response.ListingCommentResponse;
import com.matcher.matchmate_backend.dto.response.ListingLikeStatusResponse;
import com.matcher.matchmate_backend.dto.response.ListingResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ListingInteractionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingInteractionController {

    private final ListingInteractionService interactionService;

    public ListingInteractionController(ListingInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ListingLikeStatusResponse> toggleLike(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(interactionService.toggleLike(principal.getUserId(), id));
    }

    @GetMapping("/{id}/like")
    public ResponseEntity<ListingLikeStatusResponse> getLikeStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(interactionService.getLikeStatus(principal.getUserId(), id));
    }

    @GetMapping("/liked")
    public ResponseEntity<List<ListingResponse>> getLikedListings(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(interactionService.getLikedListings(principal.getUserId()));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<ListingCommentResponse>> getComments(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(interactionService.getComments(principal.getUserId(), id));
    }

    /** REST fallback for posting; the STOMP path ({@code /app/rooms/{id}/comment.send}) is equivalent. */
    @PostMapping("/{id}/comments")
    public ResponseEntity<ListingCommentResponse> addComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody ListingCommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interactionService.addComment(principal.getUserId(), id, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        interactionService.deleteComment(principal.getUserId(), commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<ListingCommentLikeStatusResponse> toggleCommentLike(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(interactionService.toggleCommentLike(principal.getUserId(), commentId));
    }
}
