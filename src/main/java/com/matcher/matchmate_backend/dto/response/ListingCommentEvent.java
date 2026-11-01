package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Envelope broadcast on {@code /topic/rooms/{listingId}/comments}.
 * <ul>
 *   <li>{@code COMMENT_CREATED}: {@link #comment} is the new comment.</li>
 *   <li>{@code COMMENT_DELETED}: only {@link #commentId} is set.</li>
 *   <li>{@code COMMENT_LIKE_UPDATED}: {@link #comment} carries the fresh {@code likesCount}.</li>
 * </ul>
 * {@link #commentsCount} is the thread size after the change, for the "Comments (N)" header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingCommentEvent {

    public enum Type { COMMENT_CREATED, COMMENT_DELETED, COMMENT_LIKE_UPDATED }

    private Type type;
    private UUID listingId;
    private UUID commentId;
    private ListingCommentResponse comment;
    private long commentsCount;
}
