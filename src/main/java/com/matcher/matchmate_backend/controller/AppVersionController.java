package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.response.AppVersionResponse;
import com.matcher.matchmate_backend.model.entity.AppBranding;
import com.matcher.matchmate_backend.service.AdminBrandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/version")
public class AppVersionController {

    private final AdminBrandingService adminBrandingService;

    public AppVersionController(AdminBrandingService adminBrandingService) {
        this.adminBrandingService = adminBrandingService;
    }

    @GetMapping
    public ResponseEntity<AppVersionResponse> getVersion() {
        AppBranding branding = adminBrandingService.getEntity();
        return ResponseEntity.ok(AppVersionResponse.builder()
                .latestBuildNumber(branding.getLatestBuildNumber() != null ? branding.getLatestBuildNumber() : 7)
                .minSupportedBuildNumber(branding.getMinSupportedBuildNumber() != null ? branding.getMinSupportedBuildNumber() : 5)
                .releaseNotes(branding.getReleaseNotes() != null ? branding.getReleaseNotes() : "New features and bug fixes.")
                .updateUrl(branding.getUpdateUrl() != null ? branding.getUpdateUrl() : "https://play.google.com/store/apps/details?id=com.mateeazy.app")
                .build());
    }
}
