package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.auth.PrimaryAuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of GET /api/v1/admin/users. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSummaryResponse {
    private UUID id;
    private String fullName;
    private String avatarUrl;
    private Gender gender;
    private Integer age;
    private String lastKnownLocality;
    private PrimaryAuthProvider primaryAuthProvider;
    private Boolean isActive;
    private Boolean isSuspended;
    private String suspendedReason;
    /** Self-service lifecycle: ACTIVE / DISABLED / DELETED. */
    private AccountStatus accountStatus;
    private Boolean onboardingCompleted;
    private OffsetDateTime createdAt;
}
