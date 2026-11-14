package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserHiddenPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserHiddenPostRepository extends JpaRepository<UserHiddenPost, UUID> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
    List<UserHiddenPost> findByUserId(UUID userId);
    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
