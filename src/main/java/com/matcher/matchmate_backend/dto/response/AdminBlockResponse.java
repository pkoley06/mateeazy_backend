package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the admin block log (GET /api/v1/admin/blocks). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBlockResponse {
    private UUID blockerId;
    private String blockerName;
    private UUID blockedId;
    private String blockedName;
    /** How many people in total have blocked the blocked user — a quick abuse signal. */
    private long blockedUserTotalBlocks;
    private OffsetDateTime createdAt;
}
