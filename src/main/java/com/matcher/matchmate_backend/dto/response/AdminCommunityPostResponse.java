package com.matcher.matchmate_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A community post as the admin sees it: the app's post fields plus open reports. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCommunityPostResponse {
    @JsonUnwrapped
    private CommunityPostResponse post;
    /** Open reports on the post and on its comments. */
    private long pendingReports;
}
