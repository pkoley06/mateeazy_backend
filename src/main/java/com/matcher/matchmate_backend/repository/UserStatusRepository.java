package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface UserStatusRepository extends JpaRepository<UserStatus, UUID> {

    List<UserStatus> findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(UUID userId, OffsetDateTime now);

    List<UserStatus> findByUserIdInAndExpiresAtAfterOrderByCreatedAtAsc(Collection<UUID> userIds, OffsetDateTime now);

    org.springframework.data.domain.Page<UserStatus> findByExpiresAtAfterOrderByCreatedAtDesc(
            OffsetDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    long countByExpiresAtAfter(OffsetDateTime now);

    @Query("SELECT COUNT(DISTINCT s.user.id) FROM UserStatus s WHERE s.expiresAt > :now")
    long countAuthorsWithLiveStatus(@Param("now") OffsetDateTime now);

    long countByUserIdAndExpiresAtAfter(UUID userId, OffsetDateTime now);

    /** Admin list. No parameter may be null ("%" for no search, any UUID with anyUser = true). */
    @Query(value = """
        SELECT s FROM UserStatus s
        JOIN FETCH s.user u
        WHERE (LOWER(COALESCE(s.caption, '')) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern)
          AND (:anyUser = TRUE OR u.id = :userId)
          AND (:onlyLive = FALSE OR s.expiresAt > :now)
          AND (:onlyExpired = FALSE OR s.expiresAt <= :now)
          AND (:onlyReported = FALSE OR EXISTS (
                SELECT r.id FROM Report r WHERE r.reportedStatus.id = s.id AND r.status = :pendingStatus))
        ORDER BY s.createdAt DESC
    """, countQuery = """
        SELECT COUNT(s) FROM UserStatus s
        JOIN s.user u
        WHERE (LOWER(COALESCE(s.caption, '')) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern)
          AND (:anyUser = TRUE OR u.id = :userId)
          AND (:onlyLive = FALSE OR s.expiresAt > :now)
          AND (:onlyExpired = FALSE OR s.expiresAt <= :now)
          AND (:onlyReported = FALSE OR EXISTS (
                SELECT r.id FROM Report r WHERE r.reportedStatus.id = s.id AND r.status = :pendingStatus))
    """)
    Page<UserStatus> adminSearch(
        @Param("pattern") String pattern,
        @Param("anyUser") boolean anyUser,
        @Param("userId") UUID userId,
        @Param("onlyLive") boolean onlyLive,
        @Param("onlyExpired") boolean onlyExpired,
        @Param("onlyReported") boolean onlyReported,
        @Param("now") OffsetDateTime now,
        @Param("pendingStatus") com.matcher.matchmate_backend.model.enums.moderation.ReportStatus pendingStatus,
        Pageable pageable
    );
}
