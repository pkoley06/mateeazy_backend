package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<UserNotification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM UserNotification n WHERE n.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);

    /** Housekeeping: keeps the inbox bounded per user (see {@code UserNotificationService}). */
    @Modifying
    @Query(value = """
        DELETE FROM user_notifications
        WHERE user_id = :userId
          AND id NOT IN (
              SELECT id FROM user_notifications
              WHERE user_id = :userId
              ORDER BY created_at DESC
              LIMIT :keep
          )
        """, nativeQuery = true)
    int trimToNewest(@Param("userId") UUID userId, @Param("keep") int keep);
}
