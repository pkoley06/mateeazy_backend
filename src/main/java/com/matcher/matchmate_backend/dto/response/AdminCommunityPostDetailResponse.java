package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/** Everything the admin post page shows, in one call. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCommunityPostDetailResponse {
    private AdminCommunityPostResponse post;
    /** Thread order: each comment is followed by its replies. */
    private List<AdminCommunityCommentResponse> comments;
    /** Reports on the post and on its comments, newest first. */
    private List<ReportResponse> reports;
    private AuthorStats author;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorStats {
        private long postsCount;
        private long liveStatuses;
        private long listingsCount;
        private boolean suspended;
        private OffsetDateTime joinedAt;
    }
}
