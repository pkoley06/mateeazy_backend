package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBrandingResponse {
    private Long id;
    private String appName;
    private String tagline;
    private String primaryColor;
    private String logoUrl;
    private String splashImageUrl;
    private String supportEmail;
    private String termsUrl;
    private String privacyUrl;
    private Boolean maintenanceMode;
    private String googleMapsApiKey;
    private String developerUpiId;
    private String developerQrCodeUrl;
    private String developerSupportNote;
    private Integer developerUpiAmount;
    private String developerUpiNote;
    private Integer deletionGracePeriodDays;
    private OffsetDateTime updatedAt;
}
