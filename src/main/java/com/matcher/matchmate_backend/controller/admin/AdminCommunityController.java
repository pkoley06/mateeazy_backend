package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.response.CommunityPostResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.dto.response.UserStatusResponse;
import com.matcher.matchmate_backend.service.AdminCommunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/community")
public class AdminCommunityController {

    private final AdminCommunityService adminCommunityService;

    public AdminCommunityController(AdminCommunityService adminCommunityService) {
        this.adminCommunityService = adminCommunityService;
    }

    @GetMapping("/posts")
    public ResponseEntity<PageResponse<CommunityPostResponse>> listPosts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminCommunityService.listPosts(search, category, page, size));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        adminCommunityService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statuses")
    public ResponseEntity<PageResponse<UserStatusResponse>> listStatuses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminCommunityService.listStatuses(page, size));
    }

    @DeleteMapping("/statuses/{id}")
    public ResponseEntity<Void> deleteStatus(@PathVariable UUID id) {
        adminCommunityService.deleteStatus(id);
        return ResponseEntity.noContent().build();
    }
}
