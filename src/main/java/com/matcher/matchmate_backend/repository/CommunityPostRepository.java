package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.CommunityPost;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query(value = """
        SELECT p.* FROM community_posts p
        JOIN app_users u ON p.author_id = u.id
        WHERE p.status = 'ACTIVE'
          AND u.is_active = true
          AND u.is_suspended = false
          AND (:category IS NULL OR p.category = :category)
          AND (
            CAST(:location AS geography) IS NULL
            OR ST_DWithin(p.location, CAST(:location AS geography), :radiusMeters)
          )
        ORDER BY p.is_pinned DESC, p.created_at DESC
        """,
        countQuery = """
        SELECT count(p.id) FROM community_posts p
        JOIN app_users u ON p.author_id = u.id
        WHERE p.status = 'ACTIVE'
          AND u.is_active = true
          AND u.is_suspended = false
          AND (:category IS NULL OR p.category = :category)
          AND (
            CAST(:location AS geography) IS NULL
            OR ST_DWithin(p.location, CAST(:location AS geography), :radiusMeters)
          )
        """,
        nativeQuery = true)
    Page<CommunityPost> findPostsWithinRadius(
        @Param("location") Point location,
        @Param("radiusMeters") double radiusMeters,
        @Param("category") String category,
        Pageable pageable
    );

    @Modifying
    @Query("UPDATE CommunityPost p SET p.likesCount = GREATEST(0, p.likesCount + :delta) WHERE p.id = :postId")
    void updateLikesCount(@Param("postId") UUID postId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE CommunityPost p SET p.commentsCount = GREATEST(0, p.commentsCount + :delta) WHERE p.id = :postId")
    void updateCommentsCount(@Param("postId") UUID postId, @Param("delta") int delta);

    @Query("""
        SELECT p FROM CommunityPost p
        LEFT JOIN p.author u
        WHERE (LOWER(p.content) LIKE :pattern
               OR LOWER(p.title) LIKE :pattern
               OR LOWER(u.fullName) LIKE :pattern)
          AND (:anyCategory = TRUE OR p.category = :category)
        ORDER BY p.createdAt DESC
    """)
    // No parameter may be null: PostgreSQL cannot infer the type of an untyped
    // NULL inside LOWER(...) / IS NULL, which made the admin list return 500.
    // `pattern` is a lower-cased "%term%" ("%" for no search); when no category
    // filter is set, pass any enum value with anyCategory = true.
    Page<CommunityPost> searchForAdmin(
        @Param("pattern") String pattern,
        @Param("anyCategory") boolean anyCategory,
        @Param("category") com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory category,
        Pageable pageable
    );

    /**
     * Admin list. No parameter may be null (see searchForAdmin): pass "%" for no
     * search, and any enum or UUID with the matching any* flag set to true.
     */
    @Query(value = """
        SELECT p FROM CommunityPost p
        JOIN FETCH p.author u
        WHERE (LOWER(p.content) LIKE :pattern
               OR LOWER(p.title) LIKE :pattern
               OR LOWER(u.fullName) LIKE :pattern)
          AND (:anyCategory = TRUE OR p.category = :category)
          AND (:anyAuthor = TRUE OR u.id = :authorId)
          AND (:onlyActive = FALSE OR p.status = :activeStatus)
          AND (:onlyHidden = FALSE OR p.status <> :activeStatus)
          AND (:onlyPinned = FALSE OR p.isPinned = TRUE)
          AND (:onlyReported = FALSE OR EXISTS (
                SELECT r.id FROM Report r WHERE r.contextPostId = p.id AND r.status = :pendingStatus))
        ORDER BY p.createdAt DESC
    """, countQuery = """
        SELECT COUNT(p) FROM CommunityPost p
        JOIN p.author u
        WHERE (LOWER(p.content) LIKE :pattern
               OR LOWER(p.title) LIKE :pattern
               OR LOWER(u.fullName) LIKE :pattern)
          AND (:anyCategory = TRUE OR p.category = :category)
          AND (:anyAuthor = TRUE OR u.id = :authorId)
          AND (:onlyActive = FALSE OR p.status = :activeStatus)
          AND (:onlyHidden = FALSE OR p.status <> :activeStatus)
          AND (:onlyPinned = FALSE OR p.isPinned = TRUE)
          AND (:onlyReported = FALSE OR EXISTS (
                SELECT r.id FROM Report r WHERE r.contextPostId = p.id AND r.status = :pendingStatus))
    """)
    Page<CommunityPost> adminSearch(
        @Param("pattern") String pattern,
        @Param("anyCategory") boolean anyCategory,
        @Param("category") com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory category,
        @Param("anyAuthor") boolean anyAuthor,
        @Param("authorId") UUID authorId,
        @Param("onlyActive") boolean onlyActive,
        @Param("onlyHidden") boolean onlyHidden,
        @Param("onlyPinned") boolean onlyPinned,
        @Param("onlyReported") boolean onlyReported,
        @Param("activeStatus") com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus activeStatus,
        @Param("pendingStatus") com.matcher.matchmate_backend.model.enums.moderation.ReportStatus pendingStatus,
        Pageable pageable
    );

    long countByStatus(com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus status);

    long countByAuthorId(UUID authorId);

    List<CommunityPost> findTop5ByOrderByCreatedAtDesc();
}
