package com.matcher.matchmate_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBrandingUpdateRequest {

    @NotBlank(message = "App name is required")
    @Size(max = 100, message = "App name cannot exceed 100 characters")
    private String appName;

    @Size(max = 255, message = "Tagline cannot exceed 255 characters")
    private String tagline;

    @NotBlank(message = "Primary color is required")
    @Pattern(regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$", message = "Primary color must be a valid hex color code")
    private String primaryColor;

    private String logoUrl;
    private String splashImageUrl;

    @Size(max = 100, message = "Support email cannot exceed 100 characters")
    private String supportEmail;

    private String termsUrl;
    private String privacyUrl;

    private Boolean maintenanceMode;

    @Size(max = 200, message = "Google Maps API key cannot exceed 200 characters")
    private String googleMapsApiKey;

    @Size(max = 100, message = "Developer UPI ID cannot exceed 100 characters")
    private String developerUpiId;

    private String developerQrCodeUrl;

    private String developerSupportNote;

    /** Rupees prefilled in the payment app; 0 clears it (the payer types the amount). */
    @jakarta.validation.constraints.Min(value = 0, message = "UPI amount cannot be negative")
    @jakarta.validation.constraints.Max(value = 100000, message = "UPI amount cannot exceed 100000")
    private Integer developerUpiAmount;

    @Size(max = 80, message = "UPI payment note cannot exceed 80 characters")
    private String developerUpiNote;

    @jakarta.validation.constraints.Min(value = 1, message = "Grace period must be at least 1 day")
    @jakarta.validation.constraints.Max(value = 365, message = "Grace period cannot exceed 365 days")
    private Integer deletionGracePeriodDays;
}
