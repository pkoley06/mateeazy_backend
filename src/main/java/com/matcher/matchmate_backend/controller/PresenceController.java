package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.response.PresenceResponse;
import com.matcher.matchmate_backend.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/{userId}/presence")
    public ResponseEntity<PresenceResponse> getPresence(@PathVariable UUID userId) {
        return ResponseEntity.ok(presenceService.getPresence(userId));
    }
}
