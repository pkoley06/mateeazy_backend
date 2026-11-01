package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.ListingCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ListingCommentLikeRepository
        extends JpaRepository<ListingCommentLike, ListingCommentLike.ListingCommentLikeId> {

    Optional<ListingCommentLike> findByCommentIdAndUserId(UUID commentId, UUID userId);

    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);

    long countByCommentId(UUID commentId);

    /** {@code [commentId, count]} rows for a batch of comments, so a thread needs one query not N. */
    @Query("SELECT l.commentId, COUNT(l) FROM ListingCommentLike l WHERE l.commentId IN :commentIds GROUP BY l.commentId")
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<UUID> commentIds);

    @Query("SELECT l.commentId FROM ListingCommentLike l WHERE l.userId = :userId AND l.commentId IN :commentIds")
    Set<UUID> findLikedCommentIds(@Param("userId") UUID userId, @Param("commentIds") Collection<UUID> commentIds);

    /** AccountPurgeService: likes the user placed on OTHER users' comments (not reachable via any cascade). */
    @Modifying
    @Query("DELETE FROM ListingCommentLike l WHERE l.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
