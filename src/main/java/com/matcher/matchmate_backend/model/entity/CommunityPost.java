package com.matcher.matchmate_backend.model.entity;

import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "community_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tagged_listing_id")
    private Listing taggedListing;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private CommunityPostCategory category;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "price_amount")
    private Integer priceAmount;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "locality_name", nullable = false, length = 150)
    private String localityName;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Builder.Default
    @Column(name = "image_urls", columnDefinition = "text[]")
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    @Column(name = "likes_count", nullable = false)
    private Integer likesCount = 0;

    @Builder.Default
    @Column(name = "comments_count", nullable = false)
    private Integer commentsCount = 0;

    @Builder.Default
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private CommunityPostStatus status = CommunityPostStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
