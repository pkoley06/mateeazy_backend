package com.matcher.matchmate_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A 24h status as the admin sees it: the app's fields plus open reports and whether it is live. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatusResponse {
    @JsonUnwrapped
    private UserStatusResponse status;
    private long pendingReports;
    private boolean live;
}
