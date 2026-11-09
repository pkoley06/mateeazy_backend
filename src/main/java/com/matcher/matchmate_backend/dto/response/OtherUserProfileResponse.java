package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.OccupationCategory;
import com.matcher.matchmate_backend.model.enums.lifestyle.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherUserProfileResponse {
    private UUID id;
    private String fullName;
    private Gender gender;
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
    private String locality;
    private Boolean isRoomSeeker;
    private Boolean isIdentityVerified;
    private String maskedDocumentId;
    private Integer activeListingsCount;
    private List<ListingResponse> activeListings;
}
