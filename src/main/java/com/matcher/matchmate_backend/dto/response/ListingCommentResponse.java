package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One comment in a room thread; also the payload broadcast over STOMP. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingCommentResponse {
    private UUID id;
    private UUID listingId;
    private UUID userId;
    private String authorName;
    private String authorAvatarUrl;
    /** Author has completed onboarding (the "verified" tick on the tile). */
    private boolean authorVerified;
    /** Author is the host of the room being discussed. */
    private boolean authorIsHost;
    private String content;
    private long likesCount;
    /**
     * Whether the requesting user liked this comment. {@code null} on broadcast frames,
     * where there is no single requesting user; clients keep their own value then.
     */
    private Boolean likedByMe;
    private OffsetDateTime createdAt;
}
