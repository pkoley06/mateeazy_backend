package com.matcher.matchmate_backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-user push notification switches (V12). One row per user, keyed by the user id;
 * a missing row means "everything on" (see {@code NotificationPreferencesService}).
 */
@Entity
@Table(name = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "notify_likes_comments", nullable = false)
    @Builder.Default
    private Boolean notifyLikesComments = true;

    @Column(name = "notify_messages", nullable = false)
    @Builder.Default
    private Boolean notifyMessages = true;

    @Column(name = "notify_seekers_nearby", nullable = false)
    @Builder.Default
    private Boolean notifySeekersNearby = true;

    @Column(name = "notify_rooms_nearby", nullable = false)
    @Builder.Default
    private Boolean notifyRoomsNearby = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static UserNotificationPreferences defaultsFor(UUID userId) {
        return UserNotificationPreferences.builder().userId(userId).build();
    }
}
