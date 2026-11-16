package com.matcher.matchmate_backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A user's like on a single community post comment (V31 {@code community_comment_likes}). */
@Entity
@Table(name = "community_comment_likes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(CommunityCommentLike.CommunityCommentLikeId.class)
public class CommunityCommentLike {

    @Id
    @Column(name = "comment_id")
    private UUID commentId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommunityCommentLikeId implements Serializable {
        private UUID commentId;
        private UUID userId;
    }
}
