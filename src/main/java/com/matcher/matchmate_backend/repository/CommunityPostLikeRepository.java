package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, CommunityPostLike.CommunityPostLikeId> {
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Modifying
    @Query("DELETE FROM CommunityPostLike l WHERE l.postId = :postId AND l.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);

    List<CommunityPostLike> findByUserIdAndPostIdIn(UUID userId, Collection<UUID> postIds);
}
