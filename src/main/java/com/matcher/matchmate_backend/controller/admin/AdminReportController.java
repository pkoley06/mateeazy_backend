package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.dto.request.AdminReportResolveRequest;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.dto.response.ReportResponse;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import com.matcher.matchmate_backend.service.AdminReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReportResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminReportService.getReports(status, type, page, size));
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<ReportResponse> resolveReport(
            @PathVariable UUID id,
            @Valid @RequestBody AdminReportResolveRequest request
    ) {
        return ResponseEntity.ok(adminReportService.resolveReport(id, request));
    }
}
