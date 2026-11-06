package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.request.AdminListingCreateRequest;
import com.matcher.matchmate_backend.dto.request.AdminListingUpdateRequest;
import com.matcher.matchmate_backend.dto.response.AdminListingDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminListingSummaryResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.service.AdminListingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/listings")
public class AdminListingController {

    private final AdminListingService adminListingService;

    public AdminListingController(AdminListingService adminListingService) {
        this.adminListingService = adminListingService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminListingSummaryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) UUID hostUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminListingService.list(search, available, hostUserId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminListingDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminListingService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AdminListingDetailResponse> create(@Valid @RequestBody AdminListingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminListingService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminListingDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminListingUpdateRequest request
    ) {
        return ResponseEntity.ok(adminListingService.update(id, request));
    }

    @PatchMapping("/{id}/available")
    public ResponseEntity<AdminListingDetailResponse> setAvailable(
            @PathVariable UUID id,
            @RequestBody SetAvailableRequest request
    ) {
        return ResponseEntity.ok(adminListingService.setAvailable(id, request.available()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminListingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record SetAvailableRequest(boolean available) {}
}
