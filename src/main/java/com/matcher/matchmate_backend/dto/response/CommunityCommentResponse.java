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
public class CommunityCommentResponse {
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private String authorName;
    private String authorAvatarUrl;
    private Boolean authorIsVerified;
    private String content;
    private OffsetDateTime createdAt;
}
