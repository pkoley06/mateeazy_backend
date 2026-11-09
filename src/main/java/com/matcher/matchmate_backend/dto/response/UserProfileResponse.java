package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.OccupationCategory;
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
import java.util.List;
import java.util.UUID;

/** GET /api/v1/users/me — full profile, used by the Profile strength screen. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID userId;
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
    private Integer profileStrength;
    private Boolean isProfileComplete;
    /** Lifestyle field names (camelCase, matching this DTO) that are still unanswered. */
    private List<String> missingLifestyleFields;
    /** Discover header "Room seeker" toggle (persisted as app_users.is_looking_for_room). */
    private Boolean isRoomSeeker;
    /** Effective Room Finder gender filter (never null: falls back to the user's own gender). */
    private com.matcher.matchmate_backend.model.enums.listing.GenderPreference roomFinderGender;
}
