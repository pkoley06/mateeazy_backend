package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** POST /api/v1/listings/comments/{id}/like */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingCommentLikeStatusResponse {
    private UUID commentId;
    private boolean likedByMe;
    private long likesCount;
}
