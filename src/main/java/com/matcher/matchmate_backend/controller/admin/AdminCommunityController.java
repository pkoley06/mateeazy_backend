package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.request.AdminPostPinRequest;
import com.matcher.matchmate_backend.dto.request.AdminPostStatusRequest;
import com.matcher.matchmate_backend.dto.request.AdminRemovePhotoRequest;
import com.matcher.matchmate_backend.dto.response.AdminCommunityCommentResponse;
import com.matcher.matchmate_backend.dto.response.AdminCommunityPostDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminCommunityPostResponse;
import com.matcher.matchmate_backend.dto.response.AdminStatusDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminStatusResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.service.AdminCommunityService;
import jakarta.validation.Valid;
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

    // ── Posts ────────────────────────────────────────────────────────────

    /** state: ALL (default), ACTIVE, HIDDEN, PINNED or REPORTED. */
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<AdminCommunityPostResponse>> listPosts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminCommunityService.listPosts(search, category, state, authorId, page, size));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<AdminCommunityPostDetailResponse> getPost(@PathVariable UUID id) {
        return ResponseEntity.ok(adminCommunityService.getPost(id));
    }

    @PatchMapping("/posts/{id}/pin")
    public ResponseEntity<AdminCommunityPostResponse> setPinned(
            @PathVariable UUID id, @Valid @RequestBody AdminPostPinRequest request) {
        return ResponseEntity.ok(adminCommunityService.setPinned(id, request.getPinned()));
    }

    /** ARCHIVED hides the post in the app (and resolves its reports); ACTIVE restores it. */
    @PatchMapping("/posts/{id}/status")
    public ResponseEntity<AdminCommunityPostResponse> setStatus(
            @PathVariable UUID id, @Valid @RequestBody AdminPostStatusRequest request) {
        return ResponseEntity.ok(adminCommunityService.setPostStatus(id, request.getStatus()));
    }

    @PostMapping("/posts/{id}/photos/remove")
    public ResponseEntity<AdminCommunityPostResponse> removePhoto(
            @PathVariable UUID id, @Valid @RequestBody AdminRemovePhotoRequest request) {
        return ResponseEntity.ok(adminCommunityService.removePhoto(id, request.getUrl()));
    }

    @PostMapping("/posts/{id}/reports/dismiss")
    public ResponseEntity<Void> dismissPostReports(@PathVariable UUID id) {
        adminCommunityService.dismissPostReports(id);
        return ResponseEntity.noContent().build();
    }

    /** Archives the post (kept for older admin builds; use PATCH /status). */
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        adminCommunityService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    // ── Comments ─────────────────────────────────────────────────────────

    @GetMapping("/comments")
    public ResponseEntity<PageResponse<AdminCommunityCommentResponse>> listComments(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean reportedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminCommunityService.listComments(search, reportedOnly, page, size));
    }

    /** Deletes the comment and its replies. */
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id) {
        adminCommunityService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{id}/reports/dismiss")
    public ResponseEntity<Void> dismissCommentReports(@PathVariable UUID id) {
        adminCommunityService.dismissCommentReports(id);
        return ResponseEntity.noContent().build();
    }

    // ── Statuses ─────────────────────────────────────────────────────────

    /** state: LIVE (default), EXPIRED, REPORTED or ALL. */
    @GetMapping("/statuses")
    public ResponseEntity<PageResponse<AdminStatusResponse>> listStatuses(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminCommunityService.listStatuses(state, search, userId, page, size));
    }

    @GetMapping("/statuses/{id}")
    public ResponseEntity<AdminStatusDetailResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(adminCommunityService.getStatus(id));
    }

    @DeleteMapping("/statuses/{id}")
    public ResponseEntity<Void> deleteStatus(@PathVariable UUID id) {
        adminCommunityService.deleteStatus(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/statuses/{id}/reports/dismiss")
    public ResponseEntity<Void> dismissStatusReports(@PathVariable UUID id) {
        adminCommunityService.dismissStatusReports(id);
        return ResponseEntity.noContent().build();
    }
}
