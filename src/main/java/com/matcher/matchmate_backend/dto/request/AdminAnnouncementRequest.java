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
     * Optional list of specific user IDs. If empty or null, broadcasts to ALL active users.
     */
    private List<UUID> userIds;
}
