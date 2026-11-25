package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.moderation.ModerationAction;
import com.matcher.matchmate_backend.model.enums.moderation.ReportReason;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private UUID id;
    private UUID reporterId;
    private String reporterName;
    private UUID reportedUserId;
    private String reportedUserName;
    private UUID reportedListingId;
    private String reportedListingTitle;
    private UUID reportedPostId;
    private String reportedPostTitle;
    private UUID reportedCommentId;
    private UUID reportedStatusId;
    // The post a POST or COMMENT report belongs to (links it to the post page).
    private UUID contextPostId;
    // Snapshot of the reported text and photo, kept after the content is removed.
    private String contentPreview;
    private String contentMediaUrl;
    // False once the reported post, comment or status no longer exists.
    private Boolean contentAvailable;
    private ReportType reportType;
    private ReportReason reason;
    private String details;
    private ReportStatus status;
    private ModerationAction actionTaken;
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;
}
