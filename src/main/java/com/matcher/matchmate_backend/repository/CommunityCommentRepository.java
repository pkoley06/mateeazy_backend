package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {
    Page<CommunityComment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);

    /** Replies below a comment, at any depth (children and grandchildren). */
    @Query("SELECT COUNT(c) FROM CommunityComment c LEFT JOIN c.parent p LEFT JOIN p.parent gp WHERE p.id = :id OR gp.id = :id")
    long countDescendants(@Param("id") UUID id);

    /** Every comment on a post, oldest first (the admin post page builds the threads). */
    @Query("SELECT c FROM CommunityComment c JOIN FETCH c.user LEFT JOIN FETCH c.parent WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
    List<CommunityComment> findAllForPost(@Param("postId") UUID postId);

    /** Ids of a comment's replies at any depth (children and grandchildren). */
    @Query("SELECT c.id FROM CommunityComment c LEFT JOIN c.parent p LEFT JOIN p.parent gp WHERE p.id = :id OR gp.id = :id")
    List<UUID> findDescendantIds(@Param("id") UUID id);

    /** Admin comment log. No parameter may be null ("%" for no search). */
    @Query(value = """
        SELECT c FROM CommunityComment c
        JOIN FETCH c.user u
        JOIN FETCH c.post p
        WHERE (LOWER(c.content) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern OR LOWER(p.title) LIKE :pattern)
          AND (:onlyReported = FALSE OR EXISTS (
                SELECT r.id FROM Report r WHERE r.reportedComment.id = c.id AND r.status = :pendingStatus))
        ORDER BY c.createdAt DESC
    """, countQuery = """
        SELECT COUNT(c) FROM CommunityComment c
        JOIN c.user u
        JOIN c.post p
        WHERE (LOWER(c.content) LIKE :pattern OR LOWER(u.fullName) LIKE :pattern OR LOWER(p.title) LIKE :pattern)
          AND (:onlyReported = FALSE OR EXISTS (
                SELECT r.id FROM Report r WHERE r.reportedComment.id = c.id AND r.status = :pendingStatus))
    """)
    Page<CommunityComment> adminSearch(
        @Param("pattern") String pattern,
        @Param("onlyReported") boolean onlyReported,
        @Param("pendingStatus") com.matcher.matchmate_backend.model.enums.moderation.ReportStatus pendingStatus,
        Pageable pageable
    );
}
