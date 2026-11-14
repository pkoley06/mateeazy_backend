package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private UUID id;
    private String fullName;
    private String avatarUrl;
    private String occupationTitle;
    private String locality;
    private Boolean isVerified;
    private String followStatus; // 'NONE', 'PENDING', 'ACCEPTED'
    private Boolean isSelf;
}
