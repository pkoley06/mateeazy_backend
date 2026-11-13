package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.request.CommunityCommentCreateRequest;
import com.matcher.matchmate_backend.dto.request.CommunityPostCreateRequest;
import com.matcher.matchmate_backend.dto.response.CommunityCommentResponse;
import com.matcher.matchmate_backend.dto.response.CommunityLikeToggleResponse;
import com.matcher.matchmate_backend.dto.response.CommunityPostResponse;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.CommunityPostService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    public CommunityPostController(CommunityPostService communityPostService) {
        this.communityPostService = communityPostService;
    }

    @GetMapping("/posts")
    public ResponseEntity<Page<CommunityPostResponse>> getFeed(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "50") Double radiusKm,
            @RequestParam(required = false) CommunityPostCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<CommunityPostResponse> feed = communityPostService.getFeed(
                currentUserId,
                lat,
                lng,
                radiusKm,
                category,
                pageable
        );
        return ResponseEntity.ok(feed);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<CommunityPostResponse> getPostById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(communityPostService.getPostById(id, currentUserId));
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityPostResponse> createPost(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody CommunityPostCreateRequest request
    ) {
        CommunityPostResponse response = communityPostService.createPost(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        communityPostService.deletePost(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<CommunityLikeToggleResponse> toggleLike(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(communityPostService.toggleLike(id, principal.getUserId()));
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<Page<CommunityCommentResponse>> getComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(communityPostService.getComments(id, pageable));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<CommunityCommentResponse> addComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody CommunityCommentCreateRequest request
    ) {
        CommunityCommentResponse response = communityPostService.addComment(id, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        communityPostService.deleteComment(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
