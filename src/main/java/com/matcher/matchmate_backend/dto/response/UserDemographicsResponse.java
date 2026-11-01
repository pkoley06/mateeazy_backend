package com.matcher.matchmate_backend.dto.response;


import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.OccupationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDemographicsResponse {
    private UUID userId;
    private String fullName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private Integer age;
    private String avatarUrl;
    private OccupationCategory occupationCategory;
    private String occupationTitle;
    private String bio;
    private Integer profileStrength;
    private Boolean isProfileComplete;
}
