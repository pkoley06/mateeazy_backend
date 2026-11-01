package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.moderation.ReportReason;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportCreateRequest {

    @NotNull(message = "Report type is required (USER or LISTING)")
    private ReportType reportType;

    private UUID reportedUserId;

    private UUID reportedListingId;

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    @Size(max = 1000, message = "Details cannot exceed 1000 characters")
    private String details;
}
