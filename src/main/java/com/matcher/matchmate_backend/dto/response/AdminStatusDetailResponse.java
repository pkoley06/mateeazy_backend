package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatusDetailResponse {
    private AdminStatusResponse status;
    /** Reports on this status, newest first. */
    private List<ReportResponse> reports;
}
