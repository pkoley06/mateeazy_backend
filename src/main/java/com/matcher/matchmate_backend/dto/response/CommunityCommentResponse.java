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
    /** Parent comment id; null for a top-level comment. */
    private UUID parentId;
    /** 0 = top-level, 1 = reply, 2 = reply to a reply. */
    private int depth;
    /** Name of the person this reply answers, for the "replying to" label. */
    private String replyToName;
    private long likesCount;
    private boolean likedByMe;
    private OffsetDateTime createdAt;
}
