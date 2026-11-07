package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);
    boolean existsByFirebaseUid(String firebaseUid);

    /** Admin console user list search (by name) - see AdminUserService. */
    Page<AppUser> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);

    /**
     * Seekers near a new room who have "Rooms listed nearby" on (no preference row = on) and
     * whose Room Finder gender filter admits the room: {@code genderPreference} is the room's
     * value ('ALL' admits everyone); a seeker with no explicit filter falls back to their own
     * gender. Seekers without a device token are included - the alert goes to their inbox.
     */
    @Query(value = """
        SELECT u.* FROM app_users u
        LEFT JOIN user_notification_preferences p ON p.user_id = u.id
        WHERE u.is_active = true
          AND u.is_suspended = false
          AND u.is_looking_for_room = true
          AND COALESCE(p.notify_rooms_nearby, true) = true
          AND u.id != :hostUserId
          AND (
                :genderPreference = 'ALL'
                OR COALESCE(u.room_finder_gender,
                            CASE u.gender WHEN 'OTHERS' THEN 'OTHER' ELSE u.gender END) = 'ALL'
                OR COALESCE(u.room_finder_gender,
                            CASE u.gender WHEN 'OTHERS' THEN 'OTHER' ELSE u.gender END) = :genderPreference
          )
          AND ST_DWithin(
                COALESCE(u.target_search_location, u.last_known_location),
                :listingLocation,
                COALESCE(u.search_radius_km, 10.0) * 1000.0
          )
        """, nativeQuery = true)
    List<AppUser> findMatchingSeekersForListing(
        @Param("listingLocation") Point listingLocation,
        @Param("hostUserId") UUID hostUserId,
        @Param("genderPreference") String genderPreference
    );

    long countByIsActiveTrue();
    long countByOnboardingCompletedTrue();
    List<AppUser> findTop5ByOrderByCreatedAtDesc();

    /** AccountPurgeScheduler: rows whose grace period has lapsed. */
    List<AppUser> findByAccountStatusAndScheduledPurgeAtBefore(AccountStatus accountStatus, OffsetDateTime cutoff);

    /**
     * FCM tokens are per-device, not per-user. Before a new user registers this device's token,
     * strip it off whoever else still holds it (e.g. an account that just signed out on this
     * same device) so a push for the old user can never land on this device while it's signed
     * in as someone else.
     */
    @Modifying
    @Query("UPDATE AppUser u SET u.fcmToken = NULL WHERE u.fcmToken = :token AND u.id <> :excludeUserId")
    void clearFcmTokenForOtherUsers(@Param("token") String token, @Param("excludeUserId") UUID excludeUserId);

    /**
     * Defensive startup reset: the in-memory {@code PresenceRegistry} always starts empty,
     * so no user is actually online right after a (re)deploy, but this persisted mirror may
     * still say otherwise if the previous process didn't shut down cleanly.
     */
    @Modifying
    @Query("UPDATE AppUser u SET u.isOnline = false WHERE u.isOnline = true")
    void resetAllOnlineStatus();
}
