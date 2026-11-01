package com.matcher.matchmate_backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One 1-to-1 conversation per unordered pair of users (V15). {@code userOne}/{@code userTwo}
 * are always persisted with {@code userOne.id < userTwo.id} (DB-level CHECK + UNIQUE), so
 * look-up from either side of the pair is a single query - see {@code ChatService} for the
 * ascending-order computation on write.
 */
@Entity
@Table(name = "conversations")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_one_id", nullable = false)
    private AppUser userOne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_two_id", nullable = false)
    private AppUser userTwo;

    /** The listing this conversation started from, if any; survives the listing being removed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "unread_count_user_one", nullable = false)
    @Builder.Default
    private Integer unreadCountUserOne = 0;

    @Column(name = "unread_count_user_two", nullable = false)
    @Builder.Default
    private Integer unreadCountUserTwo = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
