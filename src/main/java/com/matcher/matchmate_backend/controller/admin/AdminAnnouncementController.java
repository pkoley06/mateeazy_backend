package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.request.AdminAnnouncementRequest;
import com.matcher.matchmate_backend.dto.response.AdminAnnouncementResponse;
import com.matcher.matchmate_backend.service.AdminAnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/announcements")
public class AdminAnnouncementController {

    private final AdminAnnouncementService announcementService;

    public AdminAnnouncementController(AdminAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ResponseEntity<List<AdminAnnouncementResponse>> list() {
        return ResponseEntity.ok(announcementService.listAnnouncements());
    }

    @PostMapping("/broadcast")
    public ResponseEntity<AdminAnnouncementResponse> broadcast(
            @Valid @RequestBody AdminAnnouncementRequest request
    ) {
        return ResponseEntity.ok(announcementService.broadcast(request));
    }
}
