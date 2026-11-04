package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findByHostUserId(UUID hostUserId);
    List<Listing> findByIsAvailableTrue();
    long countByIsAvailableTrue();
    List<Listing> findTop5ByOrderByCreatedAtDesc();

    /** Rooms near a seeker whose hosts have "Room seekers nearby" on (no preference row = on). */
    @Query(value = """
        SELECT l.* FROM listings l
        JOIN app_users h ON l.host_user_id = h.id
        LEFT JOIN user_notification_preferences p ON p.user_id = h.id
        WHERE l.is_available = true
          AND h.is_active = true
          AND h.is_suspended = false
          AND COALESCE(p.notify_seekers_nearby, true) = true
          AND h.id != :seekerUserId
          AND h.fcm_token IS NOT NULL
          AND ST_DWithin(
                l.location,
                :seekerLocation,
                :radiusMeters
          )
        """, nativeQuery = true)
    List<Listing> findActiveListingsWithinRadius(
        @Param("seekerLocation") org.locationtech.jts.geom.Point seekerLocation,
        @Param("radiusMeters") double radiusMeters,
        @Param("seekerUserId") UUID seekerUserId
    );

    /**
     * Admin console listing search by keyword across title, locality, or city, with optional availability filter.
     */
    // :search is cast explicitly because it is used both in an "IS NULL" check and inside
    // LOWER(CONCAT(...)); without a fixed type, Hibernate/Postgres can't agree on one type for
    // both usages and binds a null search as bytea, which LOWER() then rejects.
    @Query(value = "SELECT l FROM Listing l JOIN FETCH l.hostUser WHERE " +
            "(:search IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.localityName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.city) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:isAvailable IS NULL OR l.isAvailable = :isAvailable)",
            countQuery = "SELECT COUNT(l) FROM Listing l WHERE " +
            "(:search IS NULL OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.localityName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(l.city) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "(:isAvailable IS NULL OR l.isAvailable = :isAvailable)")
    Page<Listing> searchAdminListings(
            @Param("search") String search,
            @Param("isAvailable") Boolean isAvailable,
            Pageable pageable);

    /**
     * Milestone 4 Step 4.1: PostGIS bounding pre-filter (ST_DWithin, GiST-indexed) plus exact
     * geodesic distance (ST_Distance), joined against the host's lifestyle/demographic filters.
     * Scalar-only projection — array/entity mapping happens afterwards via {@link #findAllWithHostByIdIn}.
     * See "MatchMate Technical Architecture.md" §4.3 for the base query this extends.
     * <p>
     * Two candidate sources are unioned and paginated together, ordered by distance:
     * <ul>
     *   <li>{@code LISTING} rows — available room listings (narrowed by {@code listingType} when given).</li>
     *   <li>{@code SEEKER} rows — users with Room Finder Mode on ({@code is_looking_for_room}), placed at
     *       the search location they set in Settings (falling back to their last known location). Only
     *       included when {@code includeSeekers} is true, i.e. the feed segment is "All" or "Flatmates".
     *       Rent filters do not apply to them because a seeker has no rent figure.</li>
     * </ul>
     */
    @Query(
            value = """
                    SELECT c.kind AS kind, c.candidate_id AS candidate_id, c.distance_km AS distance_km
                    FROM (
                        SELECT 'LISTING' AS kind,
                               l.id AS candidate_id,
                               ST_Distance(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000.0 AS distance_km
                        FROM listings l
                        JOIN app_users u ON u.id = l.host_user_id
                        WHERE l.is_available = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND l.host_user_id <> :currentUserId
                          AND ST_DWithin(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference = 'VEGETARIAN')
                          AND (:minRent IS NULL OR l.rent_amount >= :minRent)
                          AND (:maxRent IS NULL OR l.rent_amount <= :maxRent)
                          AND (:listingType IS NULL OR l.listing_type = :listingType)
                        UNION ALL
                        SELECT 'SEEKER' AS kind,
                               u.id AS candidate_id,
                               ST_Distance(COALESCE(u.target_search_location, u.last_known_location),
                                           ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000.0 AS distance_km
                        FROM app_users u
                        WHERE :includeSeekers = TRUE
                          AND u.is_looking_for_room = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND u.id <> :currentUserId
                          AND COALESCE(u.target_search_location, u.last_known_location) IS NOT NULL
                          AND ST_DWithin(COALESCE(u.target_search_location, u.last_known_location),
                                         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference = 'VEGETARIAN')
                    ) c
                    ORDER BY c.distance_km ASC
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
            @Param("includeSeekers") boolean includeSeekers,
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
                          AND ST_DWithin(l.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference = 'VEGETARIAN')
                          AND (:minRent IS NULL OR l.rent_amount >= :minRent)
                          AND (:maxRent IS NULL OR l.rent_amount <= :maxRent)
                          AND (:listingType IS NULL OR l.listing_type = :listingType)
                    ) + (
                        SELECT COUNT(*)
                        FROM app_users u
                        WHERE :includeSeekers = TRUE
                          AND u.is_looking_for_room = TRUE
                          AND u.onboarding_completed = TRUE
                          AND u.is_active = TRUE
                          AND u.is_suspended = FALSE
                          AND u.id <> :currentUserId
                          AND COALESCE(u.target_search_location, u.last_known_location) IS NOT NULL
                          AND ST_DWithin(COALESCE(u.target_search_location, u.last_known_location),
                                         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                          AND (:gender IS NULL OR u.gender = :gender)
                          AND (:minAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) >= :minAge)
                          AND (:maxAge IS NULL OR DATE_PART('year', AGE(u.date_of_birth)) <= :maxAge)
                          AND (:smokingOnly = FALSE OR u.smoking_habit = 'NON_SMOKER')
                          AND (:dietOnly = FALSE OR u.dietary_preference = 'VEGETARIAN')
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
            @Param("includeSeekers") boolean includeSeekers);

    /** Loads full entities (with the host fetched eagerly) for a page of candidate IDs. */
    @Query("SELECT l FROM Listing l JOIN FETCH l.hostUser WHERE l.id IN :ids")
    List<Listing> findAllWithHostByIdIn(@Param("ids") List<UUID> ids);
}
