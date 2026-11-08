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
public class AdminAnnouncementResponse {
    private UUID id;
    private String title;
    private String body;
    private String imageUrl;
    private String actionRoute;
    private Integer recipientsCount;
    private OffsetDateTime createdAt;
}
