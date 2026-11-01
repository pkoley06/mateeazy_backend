package com.matcher.matchmate_backend.event;

import java.util.UUID;

/**
 * Scalar snapshot of a freshly posted comment, published inside the write transaction and
 * consumed after commit to push a notification to the room's host. Carries no entities so
 * the listener never touches a lazy proxy outside a session.
 */
public record ListingCommentCreatedEvent(
        UUID listingId,
        String listingTitle,
        String listingLocality,
        UUID hostUserId,
        UUID authorUserId,
        String authorName,
        String content
) {
    public boolean authoredByHost() {
        return hostUserId != null && hostUserId.equals(authorUserId);
    }
}
