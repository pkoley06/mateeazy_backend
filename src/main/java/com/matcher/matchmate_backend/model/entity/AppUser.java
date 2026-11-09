package com.matcher.matchmate_backend.model.entity;

import com.matcher.matchmate_backend.model.enums.auth.*;
import com.matcher.matchmate_backend.model.enums.lifestyle.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    /** Google account email, captured from the Firebase token at sign-in. */
    @Column(name = "email", length = 255)
    private String email;

    /** E.164 phone number, captured from the Firebase token at sign-in. */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_auth_provider", nullable = false, length = 20)
    private PrimaryAuthProvider primaryAuthProvider;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation_category", length = 30)
    private OccupationCategory occupationCategory;

    @Column(name = "occupation_title", length = 100)
    private String occupationTitle;

    @Column(name = "bio", length = 500)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "smoking_habit", length = 20)
    private SmokingHabit smokingHabit;

    @Enumerated(EnumType.STRING)
    @Column(name = "dietary_preference", length = 20)
    private DietaryPreference dietaryPreference;

    @Enumerated(EnumType.STRING)
    @Column(name = "sleep_schedule", length = 20)
    private SleepSchedule sleepSchedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanliness_level", length = 20)
    private CleanlinessLevel cleanlinessLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "guest_tolerance", length = 20)
    private GuestTolerance guestTolerance;

    @Column(name = "last_known_location", columnDefinition = "geography(Point,4326)")
    private Point lastKnownLocation;

    @Column(name = "last_known_locality", length = 100)
    @Builder.Default
    private String lastKnownLocality = "Kolkata";

    @Column(name = "is_active", nullable = false)
    @Builder.Default    
    private Boolean isActive = true;

    @Column(name = "is_suspended", nullable = false)
    @Builder.Default
    private Boolean isSuspended = false;

    @Column(name = "suspended_reason", length = 255)
    private String suspendedReason;

    /** Self-service lifecycle (ACTIVE / DISABLED / DELETED). {@code isActive} stays the single "may use the app" flag. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    @Column(name = "deletion_requested_at")
    private OffsetDateTime deletionRequestedAt;

    @Column(name = "scheduled_purge_at")
    private OffsetDateTime scheduledPurgeAt;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private Boolean onboardingCompleted = false;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "target_search_location", columnDefinition = "geography(Point,4326)")
    private Point targetSearchLocation;

    @Column(name = "target_search_locality", length = 100)
    private String targetSearchLocality;

    @Column(name = "search_radius_km")
    @Builder.Default
    private Double searchRadiusKm = 10.0;

    /** Room Finder Mode. Push toggles live in {@link UserNotificationPreferences} since V12. */
    @Column(name = "is_looking_for_room", nullable = false)
    @Builder.Default
    private Boolean isLookingForRoom = false; // off until the user opts in

    /**
     * Room Finder Mode gender filter (V19): only rooms whose {@code genderPreference} is ALL
     * or equals this are shown / pushed while the mode is on. NULL = the user's own gender;
     * ALL = no filtering. See {@link com.matcher.matchmate_backend.model.enums.listing.GenderPreference#effectiveRoomFinderGender}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "room_finder_gender", length = 20)
    private com.matcher.matchmate_backend.model.enums.listing.GenderPreference roomFinderGender;

    public com.matcher.matchmate_backend.model.enums.listing.GenderPreference effectiveRoomFinderGender() {
        return com.matcher.matchmate_backend.model.enums.listing.GenderPreference
                .effectiveRoomFinderGender(roomFinderGender, gender);
    }

    /** Live chat presence mirror (V15). The in-memory {@code PresenceRegistry} is the live
     *  source of truth while the process is up; this column is only a durable snapshot for
     *  "last seen" after going offline and for surviving a restart. */
    @Column(name = "is_online", nullable = false)
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    /** Identity verification (V23). Aadhaar / PAN verified through client Verhoeff / format check. */
    @Column(name = "is_identity_verified", nullable = false)
    @Builder.Default
    private Boolean isIdentityVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_document_type", length = 30)
    private com.matcher.matchmate_backend.model.enums.auth.VerificationDocumentType verificationDocumentType;

    @Column(name = "masked_document_id", length = 30)
    private String maskedDocumentId;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

}
