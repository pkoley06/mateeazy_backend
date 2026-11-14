package com.matcher.matchmate_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PATCH /api/v1/users/me/notification-preferences. Every field is optional:
 * {@code null} keeps the stored value, so a single switch can be flipped on its own.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesRequest {
    private Boolean notifyLikesComments;
    private Boolean notifyMessages;
    private Boolean notifySeekersNearby;
    private Boolean notifyRoomsNearby;
    private Boolean notifyFollowers;
    private Boolean notifyStatusReactions;
}
