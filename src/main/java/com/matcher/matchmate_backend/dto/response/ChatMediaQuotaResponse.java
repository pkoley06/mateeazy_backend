package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET /api/v1/chat/media-quota: the daily photo allowance and how much of it is used. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMediaQuotaResponse {
    private int limit;
    private int used;
    private int remaining;
}
