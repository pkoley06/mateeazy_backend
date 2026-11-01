package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** {@code GET /api/v1/users/{userId}/presence}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceResponse {
    private Boolean isOnline;
    private OffsetDateTime lastSeenAt;
}
