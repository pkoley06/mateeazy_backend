package com.matcher.matchmate_backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_branding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppBranding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_name", nullable = false, length = 100)
    @Builder.Default
    private String appName = "MateEazy";

    @Column(name = "tagline", length = 255)
    @Builder.Default
    private String tagline = "Find your ideal flatmate & room";

    @Column(name = "primary_color", nullable = false, length = 10)
    @Builder.Default
    private String primaryColor = "#6750A4";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "splash_image_url", length = 500)
    private String splashImageUrl;

    @Column(name = "support_email", length = 100)
    @Builder.Default
    private String supportEmail = "support@mateeazy.com";

    @Column(name = "terms_url", length = 500)
    private String termsUrl;

    @Column(name = "privacy_url", length = 500)
    private String privacyUrl;

    @Column(name = "maintenance_mode", nullable = false)
    @Builder.Default
    private Boolean maintenanceMode = false;

    @Column(name = "google_maps_api_key", length = 200)
    private String googleMapsApiKey;

    @Column(name = "developer_upi_id", length = 100)
    @Builder.Default
    private String developerUpiId = "mateeazy.dev@upi";

    @Column(name = "developer_qr_code_url", columnDefinition = "TEXT")
    private String developerQrCodeUrl;

    @Column(name = "developer_support_note", columnDefinition = "TEXT")
    @Builder.Default
    private String developerSupportNote = "Made with ❤️ for flatmates everywhere. If MateEazy helped you find a home, buy the developer a coffee!";

    /** Days a self-deleted account sits in PENDING_DELETION before AccountPurgeScheduler purges it. */
    @Column(name = "deletion_grace_period_days", nullable = false)
    @Builder.Default
    private Integer deletionGracePeriodDays = 30;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
