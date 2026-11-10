package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findByHostUserId(UUID hostUserId);

    /** The newest room a host still has open - the one a chat with them is about. */
    Optional<Listing> findFirstByHostUserIdAndIsAvailableTrueOrderByCreatedAtDesc(UUID hostUserId);
    List<Listing> findByIsAvailableTrue();
    long countByIsAvailableTrue();
    List<Listing> findTop5ByOrderByCreatedAtDesc();

    /**
     * Rooms near a seeker whose hosts have "Room seekers nearby" on (no preference row = on)
     * and whose gender preference admits the seeker ({@code seekerGender} null = no filter).
     * Hosts without a device token are included: the alert is stored in their inbox.
     */
    @Query(value = """
        SELECT l.* FROM listings l
        JOIN app_users h ON l.host_user_id = h.id
        LEFT JOIN user_notification_preferences p ON p.user_id = h.id
        WHERE l.is_available = true
          AND h.is_active = true
          AND h.is_suspended = false
          AND COALESCE(p.notify_seekers_nearby, true) = true
          AND h.id != :seekerUserId
          AND (:seekerGender IS NULL OR l.gender_preference IS NULL
               OR l.gender_preference = 'ALL' OR l.gender_preference = :seekerGender)
          AND ST_DWithin(
                l.location,
                :seekerLocation,
                :radiusMeters
          )
        """, nativeQuery = true)
    List<Listing> findActiveListingsWithinRadius(
        @Param("seekerLocation") org.locationtech.jts.geom.Point seekerLocation,
        @Param("radiusMeters") double radiusMeters,
        @Param("seekerUserId") UUID seekerUserId,
        @Param("seekerGender") String seekerGender
    );

    /**
     * Admin console listing search by keyword across title, locality, or city, with optional availability filter.
     */
    // :search is cast explicitly because it is used both in an "IS NULL" check and inside
    // LOWER(CONCAT(...)); without a fixed type, Hibernate/Postgres can't agree on one type for
    // both usages and binds a null search as bytea, which LOWER() then rejects.
    @Query(value = "SELECT l FROM Listing l JOIN FETCH l.hostUser WHERE " +
            "(:search IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.localityName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.city) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:isAvailable IS NULL OR l.isAvailable = :isAvailable) AND " +
            "(:hostUserId IS NULL OR l.hostUser.id = :hostUserId)",
            countQuery = "SELECT COUNT(l) FROM Listing l WHERE " +
            "(:search IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.localityName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.city) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:isAvailable IS NULL OR l.isAvailable = :isAvailable) AND " +
            "(:hostUserId IS NULL OR l.hostUser.id = :hostUserId)")
    Page<Listing> searchAdminListings(
            @Param("search") String search,
            @Param("isAvailable") Boolean isAvailable,
            @Param("hostUserId") UUID hostUserId,
            Pageable pageable);

    /**
     * Milestone 4 Step 4.1: PostGIS bounding pre-filter (ST_DWithin, GiST-indexed) plus exact
     * geodesic distance (ST_Distance), joined against the host's lifestyle/demographic filters.
     * Scalar-only projection — array/entity mapping happens afterwards via {@link #findAllWithHostByIdIn}.
     * See "MatchMate Technical Architecture.md" §4.3 for the base query this extends.
     * <p>
     * Two candidate sources are unioned and paginated together:
     * <ul>
     *   <li>{@code LISTING} rows — available room listings (narrowed by {@code listingType}, {@code roomType}, {@code furnishingStatus}).</li>
     *   <li>{@code SEEKER} rows — users with Room Finder Mode on ({@code is_looking_for_room}), placed at
     *       the search location they set in Settings (falling back to their last known location). Only
     *       included when {@code includeSeekers} is true, i.e. the feed segment is "All" or "Flatmates".
     *       Rent and room filters do not apply to them because a seeker has no rent figure.</li>
     * </ul>
     */
    @Query(
            value = """
                    SELECT c.kind AS kind, c.candidate_id AS candidate_id, c.distance_km AS distance_km
                    FROM (
                        SELECT 'LISTING' AS kind,
                               l.id AS candidate_id,
                               ST_Distance(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000.0 AS distance_km,
                               l.rent_amount AS rent_amount,
                               l.created_at AS created_at,
                               CASE
                                 WHEN :viewerHasLifestyle = FALSE THEN 0.0
                                 WHEN u.smoking_habit IS NULL OR u.cleanliness_level IS NULL OR u.sleep_schedule IS NULL OR u.guest_tolerance IS NULL OR u.dietary_preference IS NULL THEN NULL
                                 WHEN (:viewerSmoking = 'NON_SMOKER' AND u.smoking_habit = 'REGULAR_SMOKER') OR (:viewerSmoking = 'REGULAR_SMOKER' AND u.smoking_habit = 'NON_SMOKER') THEN 0.0
                                 ELSE (
                                   100.0 - (
                                     ( 2.0 * ABS(:viewerCleanliness - CASE u.cleanliness_level WHEN 'VERY_CLEAN' THEN 1.0 WHEN 'MODERATE' THEN 0.5 ELSE 0.0 END)
                                     + 1.5 * ABS(:viewerSleep - CASE u.sleep_schedule WHEN 'EARLY_BIRD' THEN 1.0 WHEN 'FLEXIBLE' THEN 0.5 ELSE 0.0 END)
                                     + 1.2 * ABS(:viewerGuest - CASE u.guest_tolerance WHEN 'FREQUENT' THEN 1.0 WHEN 'OCCASIONAL' THEN 0.5 ELSE 0.0 END)
                                     + 1.0 * ABS(:viewerDiet - CASE u.dietary_preference WHEN 'VEGAN' THEN 1.0 WHEN 'VEGETARIAN' THEN 0.8 WHEN 'EGGETARIAN' THEN 0.6 WHEN 'NO_PREFERENCE' THEN 0.4 ELSE 0.0 END)
                                     ) / 5.7 * 100.0
                                   )
                                 )
                               END AS match_score
                        FROM listings l
                        JOIN app_users u ON u.id = l.host_user_id
                        WHERE l.is_available = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND l.host_user_id <> :currentUserId
                          AND NOT EXISTS (
                              SELECT 1 FROM user_blocks ub
                              WHERE (ub.blocker_id = :currentUserId AND ub.blocked_id = l.host_user_id)
                                 OR (ub.blocker_id = l.host_user_id AND ub.blocked_id = :currentUserId)
                          )
                          AND ST_DWithin(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference IN ('VEGETARIAN', 'VEGAN'))
                          AND (:minRent IS NULL OR l.rent_amount >= :minRent)
                          AND (:maxRent IS NULL OR l.rent_amount <= :maxRent)
                          AND (:listingType IS NULL OR l.listing_type = :listingType)
                          AND (:roomType IS NULL OR l.room_type = :roomType)
                          AND (:furnishingStatus IS NULL OR l.furnishing_status = :furnishingStatus)
                          AND (:viewerGender IS NULL OR l.gender_preference IS NULL
                               OR l.gender_preference = 'ALL' OR l.gender_preference = :viewerGender)
                        UNION ALL
                        SELECT 'SEEKER' AS kind,
                               u.id AS candidate_id,
                               ST_Distance(COALESCE(u.target_search_location, u.last_known_location),
                                           ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000.0 AS distance_km,
                               NULL::integer AS rent_amount,
                               u.created_at AS created_at,
                               CASE
                                 WHEN :viewerHasLifestyle = FALSE THEN 0.0
                                 WHEN u.smoking_habit IS NULL OR u.cleanliness_level IS NULL OR u.sleep_schedule IS NULL OR u.guest_tolerance IS NULL OR u.dietary_preference IS NULL THEN NULL
                                 WHEN (:viewerSmoking = 'NON_SMOKER' AND u.smoking_habit = 'REGULAR_SMOKER') OR (:viewerSmoking = 'REGULAR_SMOKER' AND u.smoking_habit = 'NON_SMOKER') THEN 0.0
                                 ELSE (
                                   100.0 - (
                                     ( 2.0 * ABS(:viewerCleanliness - CASE u.cleanliness_level WHEN 'VERY_CLEAN' THEN 1.0 WHEN 'MODERATE' THEN 0.5 ELSE 0.0 END)
                                     + 1.5 * ABS(:viewerSleep - CASE u.sleep_schedule WHEN 'EARLY_BIRD' THEN 1.0 WHEN 'FLEXIBLE' THEN 0.5 ELSE 0.0 END)
                                     + 1.2 * ABS(:viewerGuest - CASE u.guest_tolerance WHEN 'FREQUENT' THEN 1.0 WHEN 'OCCASIONAL' THEN 0.5 ELSE 0.0 END)
                                     + 1.0 * ABS(:viewerDiet - CASE u.dietary_preference WHEN 'VEGAN' THEN 1.0 WHEN 'VEGETARIAN' THEN 0.8 WHEN 'EGGETARIAN' THEN 0.6 WHEN 'NO_PREFERENCE' THEN 0.4 ELSE 0.0 END)
                                     ) / 5.7 * 100.0
                                   )
                                 )
                               END AS match_score
                        FROM app_users u
                        WHERE :includeSeekers = TRUE
                          AND (:roomType IS NULL AND :furnishingStatus IS NULL)
                          AND u.is_looking_for_room = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND u.id <> :currentUserId
                          AND NOT EXISTS (
                              SELECT 1 FROM user_blocks ub
                              WHERE (ub.blocker_id = :currentUserId AND ub.blocked_id = u.id)
                                 OR (ub.blocker_id = u.id AND ub.blocked_id = :currentUserId)
                          )
                          AND COALESCE(u.target_search_location, u.last_known_location) IS NOT NULL
                          AND ST_DWithin(COALESCE(u.target_search_location, u.last_known_location),
                                         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:viewerGender IS NULL OR u.room_finder_gender IS NULL
                               OR u.room_finder_gender = 'ALL' OR u.room_finder_gender = :viewerGender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference IN ('VEGETARIAN', 'VEGAN'))
                    ) c
                    ORDER BY
                      CASE WHEN :sortBy = 'PRICE_LOW_HIGH' THEN (c.rent_amount IS NULL)::int END ASC,
                      CASE WHEN :sortBy = 'PRICE_LOW_HIGH' THEN c.rent_amount END ASC,
                      CASE WHEN :sortBy = 'PRICE_HIGH_LOW' THEN (c.rent_amount IS NULL)::int END ASC,
                      CASE WHEN :sortBy = 'PRICE_HIGH_LOW' THEN c.rent_amount END DESC,
                      CASE WHEN :sortBy = 'NEWEST' THEN c.created_at END DESC,
                      CASE WHEN :sortBy = 'BEST_MATCH' THEN (c.match_score IS NULL)::int END ASC,
                      CASE WHEN :sortBy = 'BEST_MATCH' THEN c.match_score END DESC,
                      c.distance_km ASC
                    LIMIT :limit OFFSET :offset
                    """,
            nativeQuery = true)
    List<DiscoveryCandidateProjection> findCandidateIdsWithDistance(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters,
            @Param("currentUserId") UUID currentUserId,
            @Param("gender") String gender,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("smokingOnly") boolean smokingOnly,
            @Param("dietOnly") boolean dietOnly,
            @Param("minRent") Integer minRent,
            @Param("maxRent") Integer maxRent,
            @Param("listingType") String listingType,
            @Param("roomType") String roomType,
            @Param("furnishingStatus") String furnishingStatus,
            @Param("includeSeekers") boolean includeSeekers,
            @Param("viewerGender") String viewerGender,
            @Param("sortBy") String sortBy,
            @Param("viewerHasLifestyle") boolean viewerHasLifestyle,
            @Param("viewerSmoking") String viewerSmoking,
            @Param("viewerCleanliness") double viewerCleanliness,
            @Param("viewerSleep") double viewerSleep,
            @Param("viewerGuest") double viewerGuest,
            @Param("viewerDiet") double viewerDiet,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** Same WHERE clauses as {@link #findCandidateIdsWithDistance}, without LIMIT/OFFSET, for pagination totals. */
    @Query(
            value = """
                    SELECT (
                        SELECT COUNT(*)
                        FROM listings l
                        JOIN app_users u ON u.id = l.host_user_id
                        WHERE l.is_available = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND l.host_user_id <> :currentUserId
                          AND NOT EXISTS (
                              SELECT 1 FROM user_blocks ub
                              WHERE (ub.blocker_id = :currentUserId AND ub.blocked_id = l.host_user_id)
                                 OR (ub.blocker_id = l.host_user_id AND ub.blocked_id = :currentUserId)
                          )
                          AND ST_DWithin(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference IN ('VEGETARIAN', 'VEGAN'))
                          AND (:minRent IS NULL OR l.rent_amount >= :minRent)
                          AND (:maxRent IS NULL OR l.rent_amount <= :maxRent)
                          AND (:listingType IS NULL OR l.listing_type = :listingType)
                          AND (:roomType IS NULL OR l.room_type = :roomType)
                          AND (:furnishingStatus IS NULL OR l.furnishing_status = :furnishingStatus)
                          AND (:viewerGender IS NULL OR l.gender_preference IS NULL
                               OR l.gender_preference = 'ALL' OR l.gender_preference = :viewerGender)
                    ) + (
                        SELECT COUNT(*)
                        FROM app_users u
                        WHERE :includeSeekers = TRUE
                          AND (:roomType IS NULL AND :furnishingStatus IS NULL)
                          AND u.is_looking_for_room = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND u.id <> :currentUserId
                          AND NOT EXISTS (
                              SELECT 1 FROM user_blocks ub
                              WHERE (ub.blocker_id = :currentUserId AND ub.blocked_id = u.id)
                                 OR (ub.blocker_id = u.id AND ub.blocked_id = :currentUserId)
                          )
                          AND COALESCE(u.target_search_location, u.last_known_location) IS NOT NULL
                          AND ST_DWithin(COALESCE(u.target_search_location, u.last_known_location),
                                         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:viewerGender IS NULL OR u.room_finder_gender IS NULL
                               OR u.room_finder_gender = 'ALL' OR u.room_finder_gender = :viewerGender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference IN ('VEGETARIAN', 'VEGAN'))
                    )
                    """,
            nativeQuery = true)
    long countCandidates(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") double radiusMeters,
            @Param("currentUserId") UUID currentUserId,
            @Param("gender") String gender,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("smokingOnly") boolean smokingOnly,
            @Param("dietOnly") boolean dietOnly,
            @Param("minRent") Integer minRent,
            @Param("maxRent") Integer maxRent,
            @Param("listingType") String listingType,
            @Param("roomType") String roomType,
            @Param("furnishingStatus") String furnishingStatus,
            @Param("includeSeekers") boolean includeSeekers,
            @Param("viewerGender") String viewerGender);

    /** Loads full entities (with the host fetched eagerly) for a page of candidate IDs. */
    @Query("SELECT l FROM Listing l JOIN FETCH l.hostUser WHERE l.id IN :ids")
    List<Listing> findAllWithHostByIdIn(@Param("ids") List<UUID> ids);
}
