package com.matcher.matchmate_backend.dto.response;

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
public class UserStatusFeedGroup {
    private UUID userId;
    private String userName;
    private String userAvatarUrl;
    private Boolean isSelf;
    private Boolean hasUnviewed;
    private List<UserStatusResponse> statuses;
}
