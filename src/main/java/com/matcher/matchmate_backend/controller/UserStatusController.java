package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.request.UserStatusCreateRequest;
import com.matcher.matchmate_backend.dto.response.UserStatusFeedGroup;
import com.matcher.matchmate_backend.dto.response.UserStatusResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statuses")
@RequiredArgsConstructor
public class UserStatusController {

    private final UserStatusService userStatusService;

    @PostMapping
    public ResponseEntity<UserStatusResponse> createStatus(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody UserStatusCreateRequest request
    ) {
        UserStatusResponse response = userStatusService.createStatus(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/feed")
    public ResponseEntity<List<UserStatusFeedGroup>> getActiveFeed(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(userStatusService.getActiveFeed(currentUserId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userStatusService.deleteStatus(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordView(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userStatusService.recordView(id, principal.getUserId());
        return ResponseEntity.ok().build();
    }
}
