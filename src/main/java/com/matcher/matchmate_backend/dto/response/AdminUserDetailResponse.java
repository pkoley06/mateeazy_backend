package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.OccupationCategory;
import com.matcher.matchmate_backend.model.enums.lifestyle.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full details of a user for the admin view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {

    private UUID id;
    private String firebaseUid;
    private String fullName;
    private String email;
    private String phoneNumber;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String avatarUrl;
    private OccupationCategory occupationCategory;
    private String occupationTitle;
    private String bio;
    private SmokingHabit smokingHabit;
    private DietaryPreference dietaryPreference;
    private SleepSchedule sleepSchedule;
    private CleanlinessLevel cleanlinessLevel;
    private GuestTolerance guestTolerance;
    private Double lastKnownLat;
    private Double lastKnownLng;
    private String lastKnownLocality;
    private Boolean isActive;
    private Boolean isSuspended;
    private String suspendedReason;
    /** Self-service lifecycle: ACTIVE / DISABLED / DELETED. */
    private AccountStatus accountStatus;
    private OffsetDateTime disabledAt;
    private Boolean isLookingForRoom;
    private NotificationPreferencesResponse notificationPreferences;
    private Boolean onboardingCompleted;
    private Boolean isIdentityVerified;
    private String verificationDocumentType;
    private String maskedDocumentId;
    private OffsetDateTime verifiedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
