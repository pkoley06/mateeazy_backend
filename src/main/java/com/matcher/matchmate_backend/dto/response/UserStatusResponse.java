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
public class UserStatusResponse {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String authorAvatarUrl;
    private String mediaUrl;
    private String caption;
    private String backgroundColor;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private Long viewsCount;
    private Boolean isViewedByMe;
    private Boolean isSelf;
}
