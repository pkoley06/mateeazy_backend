package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.request.AdminUserCreateRequest;
import com.matcher.matchmate_backend.dto.request.AdminUserUpdateRequest;
import com.matcher.matchmate_backend.dto.response.AdminUserDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminUserSummaryResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminUserSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminUserService.list(search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AdminUserDetailResponse> create(@Valid @RequestBody AdminUserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        return ResponseEntity.ok(adminUserService.update(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<AdminUserDetailResponse> setActive(
            @PathVariable UUID id,
            @RequestBody SetActiveRequest request
    ) {
        return ResponseEntity.ok(adminUserService.setActive(id, request.active()));
    }

    @PatchMapping("/{id}/suspend")
    public ResponseEntity<AdminUserDetailResponse> setSuspended(
            @PathVariable UUID id,
            @RequestBody SetSuspendedRequest request
    ) {
        return ResponseEntity.ok(adminUserService.setSuspended(id, request.suspended(), request.reason()));
    }

    public record SetActiveRequest(boolean active) {}

    public record SetSuspendedRequest(boolean suspended, String reason) {}
}
