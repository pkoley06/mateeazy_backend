package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One entry of GET /api/v1/blocks/details — a person the caller has blocked. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedUserResponse {
    private UUID userId;
    private String fullName;
    private String avatarUrl;
    private OffsetDateTime blockedAt;
}
