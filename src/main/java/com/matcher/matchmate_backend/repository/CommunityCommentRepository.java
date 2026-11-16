package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {
    Page<CommunityComment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);

    /** Replies below a comment, at any depth (children and grandchildren). */
    @Query("SELECT COUNT(c) FROM CommunityComment c LEFT JOIN c.parent p LEFT JOIN p.parent gp WHERE p.id = :id OR gp.id = :id")
    long countDescendants(@Param("id") UUID id);
}
