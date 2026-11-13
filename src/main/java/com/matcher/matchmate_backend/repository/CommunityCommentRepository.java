package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {
    Page<CommunityComment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);
}
