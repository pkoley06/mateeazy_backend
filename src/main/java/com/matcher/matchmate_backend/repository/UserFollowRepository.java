package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, UUID> {

    Optional<UserFollow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    Page<UserFollow> findByFollowingIdAndStatus(UUID followingId, String status, Pageable pageable);

    Page<UserFollow> findByFollowerIdAndStatus(UUID followerId, String status, Pageable pageable);

    List<UserFollow> findByFollowerIdAndStatus(UUID followerId, String status);

    long countByFollowingIdAndStatus(UUID followingId, String status);

    long countByFollowerIdAndStatus(UUID followerId, String status);

    void deleteByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
}
