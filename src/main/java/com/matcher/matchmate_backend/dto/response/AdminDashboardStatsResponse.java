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
public class AdminDashboardStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long completedOnboardingUsers;
    private long totalListings;
    private long availableListings;
    private List<AdminUserSummaryResponse> recentUsers;

    // Added for the Overview page.
    private long newUsersThisWeek;
    private long communityPosts;
    private long activeCommunityPosts;
    private long liveStatuses;
    private long liveStatusAuthors;
    private long pendingReports;
    private long newReportsToday;
    /** Open reports, newest first (at most 6). */
    private List<ReportResponse> pendingReportsPreview;
    private List<AdminCommunityPostResponse> recentPosts;
    private List<AdminListingSummaryResponse> recentListings;
}
