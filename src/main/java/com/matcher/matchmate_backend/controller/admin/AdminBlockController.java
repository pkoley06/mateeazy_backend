package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.response.AdminBlockResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.service.AdminBlockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/blocks")
public class AdminBlockController {

    private final AdminBlockService adminBlockService;

    public AdminBlockController(AdminBlockService adminBlockService) {
        this.adminBlockService = adminBlockService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminBlockResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminBlockService.list(page, size));
    }

    /** Lifts a block on the blocker's behalf (e.g. a block made by mistake or by a compromised account). */
    @DeleteMapping("/{blockerId}/{blockedId}")
    public ResponseEntity<Void> remove(@PathVariable UUID blockerId, @PathVariable UUID blockedId) {
        adminBlockService.remove(blockerId, blockedId);
        return ResponseEntity.noContent().build();
    }
}
