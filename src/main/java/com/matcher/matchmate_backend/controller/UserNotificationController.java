package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.dto.response.UserNotificationResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.UserNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** The Notifications tab's stored entries (comments, likes, matches). Chat isn't stored here. */
@RestController
@RequestMapping("/api/v1/notifications")
public class UserNotificationController {

    private final UserNotificationService notificationService;

    public UserNotificationController(UserNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserNotificationResponse>> list(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(notificationService.list(principal.getUserId(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(principal.getUserId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<UserNotificationResponse> markRead(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(notificationService.markRead(principal.getUserId(), id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(principal.getUserId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable UUID id
    ) {
        notificationService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        notificationService.clearAll(principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
