package com.matcher.matchmate_backend.event;

import com.matcher.matchmate_backend.dto.response.ListingCommentEvent;

import java.util.UUID;

/**
 * Published inside the comment transaction; relayed to STOMP subscribers after commit so
 * clients never see a comment that was rolled back.
 */
public record ListingCommentChangedEvent(ListingCommentEvent payload) {

    public UUID listingId() {
        return payload.getListingId();
    }
}
