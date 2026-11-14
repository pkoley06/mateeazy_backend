package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserStatusView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserStatusViewRepository extends JpaRepository<UserStatusView, UUID> {

    boolean existsByStatusIdAndViewerId(UUID statusId, UUID viewerId);

    long countByStatusId(UUID statusId);
}
