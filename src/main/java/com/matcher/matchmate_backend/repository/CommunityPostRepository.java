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
}
