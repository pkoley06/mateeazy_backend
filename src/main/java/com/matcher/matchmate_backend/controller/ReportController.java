package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.request.ReportCreateRequest;
import com.matcher.matchmate_backend.dto.response.ReportResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> createReport(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody ReportCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createReport(principal.getUserId(), request));
    }

    @PostMapping("/blocks/{userId}")
    public ResponseEntity<Void> blockUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        reportService.blockUser(principal.getUserId(), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/blocks/{userId}")
    public ResponseEntity<Void> unblockUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        reportService.unblockUser(principal.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    public ResponseEntity<Set<UUID>> getBlockedUsers(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(reportService.getBlockedUserIds(principal.getUserId()));
    }
}
