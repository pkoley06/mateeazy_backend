package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ACTIVE shows the post in the app again; ARCHIVED hides it from everyone. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPostStatusRequest {
    @NotNull
    private CommunityPostStatus status;
}
