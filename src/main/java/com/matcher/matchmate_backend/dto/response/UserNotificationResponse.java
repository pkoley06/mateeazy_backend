package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** One row of GET /api/v1/notifications. {@code data} mirrors the push payload. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String body;
    private Map<String, String> data;
    private boolean read;
    private OffsetDateTime createdAt;
}
