package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.OccupationCategory;
import com.matcher.matchmate_backend.model.enums.auth.PrimaryAuthProvider;
import com.matcher.matchmate_backend.model.enums.lifestyle.CleanlinessLevel;
import com.matcher.matchmate_backend.model.enums.lifestyle.DietaryPreference;
import com.matcher.matchmate_backend.model.enums.lifestyle.GuestTolerance;
import com.matcher.matchmate_backend.model.enums.lifestyle.SleepSchedule;
import com.matcher.matchmate_backend.model.enums.lifestyle.SmokingHabit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** GET/POST/PUT /api/v1/admin/users/{id} — full admin-facing view of an AppUser. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {
    private UUID id;
    private PrimaryAuthProvider primaryAuthProvider;
    private String fullName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private Integer age;
    private String avatarUrl;
    private OccupationCategory occupationCategory;
    private String occupationTitle;
    private String bio;
    private SmokingHabit smokingHabit;
    private DietaryPreference dietaryPreference;
    private SleepSchedule sleepSchedule;
    private CleanlinessLevel cleanlinessLevel;
    private GuestTolerance guestTolerance;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
