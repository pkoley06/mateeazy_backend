package com.matcher.matchmate_backend.event;

import java.util.UUID;

/** A user liked (not un-liked) a room; the host may be told, subject to their preferences. */
public record ListingLikedEvent(
        UUID listingId,
        String listingTitle,
        String listingLocality,
        UUID hostUserId,
        UUID likerUserId,
        String likerName
) {
    public boolean likedByHost() {
        return hostUserId != null && hostUserId.equals(likerUserId);
    }
}
