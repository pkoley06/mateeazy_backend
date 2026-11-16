package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserStatusView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserStatusViewRepository extends JpaRepository<UserStatusView, UUID> {

    boolean existsByStatusIdAndViewerId(UUID statusId, UUID viewerId);

    long countByStatusId(UUID statusId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT v.viewer FROM UserStatusView v WHERE v.status.id = :statusId ORDER BY v.viewedAt DESC")
    java.util.List<com.matcher.matchmate_backend.model.entity.AppUser> findViewersByStatusId(
            @org.springframework.data.repository.query.Param("statusId") UUID statusId);
}
