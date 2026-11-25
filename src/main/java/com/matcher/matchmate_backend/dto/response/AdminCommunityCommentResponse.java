package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A comment on a community post, for the admin post page and comment log. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCommunityCommentResponse {
    private UUID id;
    private UUID postId;
    private String postTitle;
    private CommunityPostStatus postStatus;
    private UUID authorId;
    private String authorName;
    private String authorAvatarUrl;
    private boolean byPostAuthor;
    private String content;
    private UUID parentId;
    /** 0 = comment, 1 = reply, 2 = reply to a reply. */
    private int depth;
    private String replyToName;
    private long likesCount;
    private long pendingReports;
    private OffsetDateTime createdAt;
}
