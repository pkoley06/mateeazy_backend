package com.matcher.matchmate_backend.model.enums.moderation;

public enum ModerationAction {
    USER_SUSPENDED,
    LISTING_DELISTED,
    LISTING_DELETED,
    // A community post archived, a comment deleted or a status removed.
    CONTENT_REMOVED,
    DISMISSED,
    NO_ACTION
}
