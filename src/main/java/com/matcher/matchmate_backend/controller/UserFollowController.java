package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.response.UserSummaryResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserFollowController {

    private final UserFollowService userFollowService;
    private final AppUserRepository appUserRepository;

    @PostMapping("/{id}/follow")
    public ResponseEntity<Map<String, String>> followUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        String status = userFollowService.followUser(principal.getUserId(), id);
        return ResponseEntity.ok(Map.of("status", status));
    }

    @DeleteMapping("/{id}/unfollow")
    public ResponseEntity<Void> unfollowUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userFollowService.unfollowUser(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/follows/{id}/accept")
    public ResponseEntity<Void> acceptFollowRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userFollowService.acceptFollowRequest(principal.getUserId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/follows/{id}/reject")
    public ResponseEntity<Void> rejectFollowRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userFollowService.rejectFollowRequest(principal.getUserId(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<Page<UserSummaryResponse>> getFollowers(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(userFollowService.getFollowers(id, currentUserId, pageable));
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<Page<UserSummaryResponse>> getFollowing(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(userFollowService.getFollowing(id, currentUserId, pageable));
    }

    @GetMapping("/{id}/follow-status")
    public ResponseEntity<Map<String, String>> getFollowStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        String status = userFollowService.getFollowStatus(currentUserId, id);
        return ResponseEntity.ok(Map.of("status", status));
    }

    @GetMapping("/{id}/follow-counts")
    public ResponseEntity<Map<String, Long>> getFollowCounts(@PathVariable UUID id) {
        return ResponseEntity.ok(userFollowService.getFollowCounts(id));
    }

    @PutMapping("/privacy/direct-follow")
    public ResponseEntity<Map<String, Boolean>> toggleDirectFollow(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody Map<String, Boolean> body
    ) {
        Boolean enabled = body.getOrDefault("directFollowEnabled", true);
        AppUser user = appUserRepository.findById(principal.getUserId()).orElseThrow();
        user.setDirectFollowEnabled(enabled);
        appUserRepository.save(user);
        return ResponseEntity.ok(Map.of("directFollowEnabled", enabled));
    }
}
