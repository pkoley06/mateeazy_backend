package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByUserOneIdAndUserTwoId(UUID userOneId, UUID userTwoId);

    /** Every conversation involving this user (either side of the pair), newest activity first. */
    @Query("SELECT c FROM Conversation c WHERE c.userOne.id = :userId OR c.userTwo.id = :userId " +
            "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<Conversation> findAllInvolvingUser(@Param("userId") UUID userId);
}
