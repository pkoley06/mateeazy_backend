package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the admin comment moderation log (GET /api/v1/admin/comments). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCommentResponse {
    private UUID id;
    private UUID listingId;
    private String listingTitle;
    private String listingLocality;
    private UUID listingHostUserId;
    private UUID authorUserId;
    private String authorName;
    private String authorAvatarUrl;
    private Boolean authorSuspended;
    private String content;
    private long likesCount;
    private OffsetDateTime createdAt;
}
