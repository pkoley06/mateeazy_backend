package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.AdminBrandingUpdateRequest;
import com.matcher.matchmate_backend.dto.response.AdminBrandingResponse;
import com.matcher.matchmate_backend.model.entity.AppBranding;
import com.matcher.matchmate_backend.repository.AppBrandingRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class AdminBrandingService {

    private final AppBrandingRepository appBrandingRepository;

    public AdminBrandingService(AppBrandingRepository appBrandingRepository) {
        this.appBrandingRepository = appBrandingRepository;
    }

    public AdminBrandingResponse getBranding() {
        AppBranding branding = getOrCreateDefault();
        return toResponse(branding);
    }

    @Transactional
    public AdminBrandingResponse updateBranding(AdminBrandingUpdateRequest request) {
        AppBranding branding = getOrCreateDefault();

        if (request.getAppName() != null) branding.setAppName(request.getAppName());
        if (request.getTagline() != null) branding.setTagline(blankToNull(request.getTagline()));
        if (request.getPrimaryColor() != null) branding.setPrimaryColor(request.getPrimaryColor());
        if (request.getLogoUrl() != null) branding.setLogoUrl(blankToNull(request.getLogoUrl()));
        if (request.getSplashImageUrl() != null) branding.setSplashImageUrl(blankToNull(request.getSplashImageUrl()));
        if (request.getSupportEmail() != null) branding.setSupportEmail(blankToNull(request.getSupportEmail()));
        if (request.getTermsUrl() != null) branding.setTermsUrl(blankToNull(request.getTermsUrl()));
        if (request.getPrivacyUrl() != null) branding.setPrivacyUrl(blankToNull(request.getPrivacyUrl()));
        if (request.getMaintenanceMode() != null) branding.setMaintenanceMode(request.getMaintenanceMode());
        if (request.getGoogleMapsApiKey() != null) branding.setGoogleMapsApiKey(blankToNull(request.getGoogleMapsApiKey()));
        if (request.getDeveloperUpiId() != null) branding.setDeveloperUpiId(blankToNull(request.getDeveloperUpiId()));
        if (request.getDeveloperQrCodeUrl() != null) branding.setDeveloperQrCodeUrl(blankToNull(request.getDeveloperQrCodeUrl()));
        if (request.getDeveloperSupportNote() != null) branding.setDeveloperSupportNote(blankToNull(request.getDeveloperSupportNote()));
        if (request.getDeletionGracePeriodDays() != null) branding.setDeletionGracePeriodDays(request.getDeletionGracePeriodDays());

        return toResponse(appBrandingRepository.save(branding));
    }

    /**
     * Optional fields are PATCH-style: absent means "unchanged", while an empty
     * (or whitespace-only) string means "clear", stored as NULL so readers can
     * rely on a single "not configured" representation.
     */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private AppBranding getOrCreateDefault() {
        return appBrandingRepository.findById(1L).orElseGet(() -> {
            AppBranding defaults = AppBranding.builder()
                    .id(1L)
                    .appName("MateEazy")
                    .tagline("Find your ideal flatmate & room")
                    .primaryColor("#6750A4")
                    .supportEmail("support@mateeazy.com")
                    .maintenanceMode(false)
                    .developerUpiId("mateeazy.dev@upi")
                    .developerSupportNote("Made with ❤️ for flatmates everywhere. If MateEazy helped you find a home, buy the developer a coffee!")
                    .deletionGracePeriodDays(30)
                    .build();
            return appBrandingRepository.save(defaults);
        });
    }

    private AdminBrandingResponse toResponse(AppBranding branding) {
        return AdminBrandingResponse.builder()
                .id(branding.getId())
                .appName(branding.getAppName())
                .tagline(branding.getTagline())
                .primaryColor(branding.getPrimaryColor())
                .logoUrl(branding.getLogoUrl())
                .splashImageUrl(branding.getSplashImageUrl())
                .supportEmail(branding.getSupportEmail())
                .termsUrl(branding.getTermsUrl())
                .privacyUrl(branding.getPrivacyUrl())
                .maintenanceMode(branding.getMaintenanceMode())
                .googleMapsApiKey(branding.getGoogleMapsApiKey())
                .developerUpiId(branding.getDeveloperUpiId())
                .developerQrCodeUrl(branding.getDeveloperQrCodeUrl())
                .developerSupportNote(branding.getDeveloperSupportNote())
                .deletionGracePeriodDays(branding.getDeletionGracePeriodDays())
                .updatedAt(branding.getUpdatedAt())
                .build();
    }
}
