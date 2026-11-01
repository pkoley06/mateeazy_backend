package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);
    boolean existsByFirebaseUid(String firebaseUid);

    /** Admin console user list search (by name) - see AdminUserService. */
    Page<AppUser> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);

    /** Seekers near a new room who have "Rooms listed nearby" on (no preference row = on). */
    @Query(value = """
        SELECT u.* FROM app_users u
        LEFT JOIN user_notification_preferences p ON p.user_id = u.id
        WHERE u.is_active = true
          AND u.is_suspended = false
          AND u.is_looking_for_room = true
          AND COALESCE(p.notify_rooms_nearby, true) = true
          AND u.id != :hostUserId
          AND u.fcm_token IS NOT NULL
          AND ST_DWithin(
                COALESCE(u.target_search_location, u.last_known_location),
                :listingLocation,
                COALESCE(u.search_radius_km, 10.0) * 1000.0
          )
        """, nativeQuery = true)
    List<AppUser> findMatchingSeekersForListing(
        @Param("listingLocation") Point listingLocation,
        @Param("hostUserId") Long hostUserId
    );

    long countByIsActiveTrue();
    long countByOnboardingCompletedTrue();
    List<AppUser> findTop5ByOrderByCreatedAtDesc();

    /** AccountPurgeScheduler: rows whose grace period has lapsed. */
    List<AppUser> findByAccountStatusAndScheduledPurgeAtBefore(AccountStatus accountStatus, OffsetDateTime cutoff);
}
