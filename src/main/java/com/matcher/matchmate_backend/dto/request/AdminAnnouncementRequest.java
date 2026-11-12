package com.matcher.matchmate_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnnouncementRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 250, message = "Title must not exceed 250 characters")
    private String title;

    @NotBlank(message = "Body message is required")
    private String body;

    private String imageUrl;

    private String actionRoute;

    /**
     * Optional notification type (e.g. "APP_UPDATE", "FESTIVE_EVENT", "ANNOUNCEMENT").
     * Defaults to auto-detecting based on actionRoute/title or "FESTIVE_EVENT".
     */
    private String type;

    /**
     * If true, broadcasts to all active users. If false, targets only the users in userIds.
     */
    private Boolean broadcastAll;

    /**
     * Optional list of specific user IDs. If empty or null and broadcastAll is true, broadcasts to ALL active users.
     */
    private List<UUID> userIds;
}
