package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserStatusRepository extends JpaRepository<UserStatus, UUID> {

    List<UserStatus> findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(UUID userId, OffsetDateTime now);

    List<UserStatus> findByUserIdInAndExpiresAtAfterOrderByCreatedAtAsc(Collection<UUID> userIds, OffsetDateTime now);
}
