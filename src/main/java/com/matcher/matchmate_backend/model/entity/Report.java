package com.matcher.matchmate_backend.model.entity;

import com.matcher.matchmate_backend.model.enums.moderation.ModerationAction;
import com.matcher.matchmate_backend.model.enums.moderation.ReportReason;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id")
    private AppUser reportedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_listing_id")
    private Listing reportedListing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_post_id")
    private CommunityPost reportedPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_comment_id")
    private CommunityComment reportedComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_status_id")
    private UserStatus reportedStatus;

    /** The post a POST or COMMENT report belongs to. Kept after the content is deleted. */
    @Column(name = "context_post_id")
    private UUID contextPostId;

    /** What was reported, as it was at the time (post title + text, comment, status caption). */
    @Column(name = "content_preview", columnDefinition = "TEXT")
    private String contentPreview;

    /** First photo of a reported post, or a status's photo, kept after the content goes. */
    @Column(name = "content_media_url", length = 1024)
    private String contentMediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private ReportReason reason;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", length = 50)
    private ModerationAction actionTaken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
