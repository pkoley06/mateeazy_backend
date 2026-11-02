package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.UserBlockId> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("SELECT b.blockedId FROM UserBlock b WHERE b.blockerId = :blockerId")
    Set<UUID> findBlockedUserIdsByBlockerId(@Param("blockerId") UUID blockerId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /** Everyone who has blocked this user (the reverse direction of findBlockedUserIdsByBlockerId). */
    @Query("SELECT b.blockerId FROM UserBlock b WHERE b.blockedId = :blockedId")
    Set<UUID> findBlockerIdsByBlockedId(@Param("blockedId") UUID blockedId);

    /** True when either user has blocked the other. */
    @Query("SELECT COUNT(b) > 0 FROM UserBlock b WHERE (b.blockerId = :a AND b.blockedId = :b) OR (b.blockerId = :b AND b.blockedId = :a)")
    boolean existsBlockBetween(@Param("a") UUID a, @Param("b") UUID b);

    long countByBlockedId(UUID blockedId);

    long countByBlockerId(UUID blockerId);

    Page<UserBlock> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** AccountPurgeService: block relationships involving the purged user, as either party. */
    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blockerId = :userId OR b.blockedId = :userId")
    void deleteAllInvolvingUser(@Param("userId") UUID userId);
}
