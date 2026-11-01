package com.matcher.matchmate_backend.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private UUID userId;
    private Boolean isProfileComplete;
    private Boolean pendingDeletion;
    private OffsetDateTime scheduledPurgeAt;
    private PreFilledUserData preFilledUserData;
}
