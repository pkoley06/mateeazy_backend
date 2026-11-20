package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.entity.UserNotificationPreferences;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** GET/PATCH /api/v1/users/me/notification-preferences, also embedded in the admin user detail. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesResponse {
    private boolean notifyLikesComments;
    private boolean notifyMessages;
    private boolean notifySeekersNearby;
    private boolean notifyRoomsNearby;
    private boolean notifyFollowers;
    private boolean notifyStatusReactions;
    private boolean notifyCommentReplies;
    private OffsetDateTime updatedAt;

    public static NotificationPreferencesResponse from(UserNotificationPreferences prefs) {
        return NotificationPreferencesResponse.builder()
                .notifyLikesComments(Boolean.TRUE.equals(prefs.getNotifyLikesComments()))
                .notifyMessages(Boolean.TRUE.equals(prefs.getNotifyMessages()))
                .notifySeekersNearby(Boolean.TRUE.equals(prefs.getNotifySeekersNearby()))
                .notifyRoomsNearby(Boolean.TRUE.equals(prefs.getNotifyRoomsNearby()))
                .notifyFollowers(Boolean.TRUE.equals(prefs.getNotifyFollowers()))
                .notifyStatusReactions(Boolean.TRUE.equals(prefs.getNotifyStatusReactions()))
                .notifyCommentReplies(Boolean.TRUE.equals(prefs.getNotifyCommentReplies()))
                .updatedAt(prefs.getUpdatedAt())
                .build();
    }
}
