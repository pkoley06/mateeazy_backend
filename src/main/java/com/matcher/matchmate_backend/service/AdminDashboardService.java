package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.AdminDashboardStatsResponse;
import com.matcher.matchmate_backend.dto.response.AdminListingSummaryResponse;
import com.matcher.matchmate_backend.dto.response.AdminUserSummaryResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.ReportRepository;
import com.matcher.matchmate_backend.repository.UserStatusRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Period;
import java.util.List;

@Service
public class AdminDashboardService {

    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final CommunityPostRepository postRepository;
    private final UserStatusRepository statusRepository;
    private final ReportRepository reportRepository;
    private final ReportService reportService;
    private final AdminCommunityService adminCommunityService;

    public AdminDashboardService(
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            CommunityPostRepository postRepository,
            UserStatusRepository statusRepository,
            ReportRepository reportRepository,
            ReportService reportService,
            AdminCommunityService adminCommunityService
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.postRepository = postRepository;
        this.statusRepository = statusRepository;
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.adminCommunityService = adminCommunityService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getStats() {
        OffsetDateTime now = OffsetDateTime.now();
        // "Today" in India, where the app's users are.
        OffsetDateTime startOfToday = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                .atStartOfDay(ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
        List<CommunityPost> recentPosts = postRepository.findTop5ByOrderByCreatedAtDesc();
        var pendingByPost = adminCommunityService.pendingByPost(recentPosts.stream().map(CommunityPost::getId).toList());

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByIsActiveTrue();
        long completedOnboardingUsers = userRepository.countByOnboardingCompletedTrue();

        long totalListings = listingRepository.count();
        long availableListings = listingRepository.countByIsAvailableTrue();

        List<AdminUserSummaryResponse> recentUsers = userRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toUserSummary)
                .toList();

        List<AdminListingSummaryResponse> recentListings = listingRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toListingSummary)
                .toList();

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .completedOnboardingUsers(completedOnboardingUsers)
                .totalListings(totalListings)
                .availableListings(availableListings)
                .recentUsers(recentUsers)
                .recentListings(recentListings)
                .newUsersThisWeek(userRepository.countByCreatedAtAfter(now.minusDays(7)))
                .communityPosts(postRepository.count())
                .activeCommunityPosts(postRepository.countByStatus(CommunityPostStatus.ACTIVE))
                .liveStatuses(statusRepository.countByExpiresAtAfter(now))
                .liveStatusAuthors(statusRepository.countAuthorsWithLiveStatus(now))
                .pendingReports(reportRepository.countByStatus(ReportStatus.PENDING))
                .newReportsToday(reportRepository.countByStatusAndCreatedAtAfter(ReportStatus.PENDING, startOfToday))
                .pendingReportsPreview(reportRepository
                        .searchReports(ReportStatus.PENDING, null, PageRequest.of(0, 6))
                        .map(reportService::toResponse)
                        .getContent())
                .recentPosts(recentPosts.stream()
                        .map(p -> adminCommunityService.toAdminPost(p, pendingByPost.getOrDefault(p.getId(), 0L)))
                        .toList())
                .build();
    }

    private AdminUserSummaryResponse toUserSummary(AppUser user) {
        return AdminUserSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .gender(user.getGender())
                .age(user.getDateOfBirth() != null ? Period.between(user.getDateOfBirth(), LocalDate.now()).getYears() : null)
                .lastKnownLocality(user.getLastKnownLocality())
                .primaryAuthProvider(user.getPrimaryAuthProvider())
                .isActive(user.getIsActive())
                .onboardingCompleted(user.getOnboardingCompleted())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AdminListingSummaryResponse toListingSummary(Listing listing) {
        return AdminListingSummaryResponse.builder()
                .id(listing.getId())
                .title(listing.getTitle())
                .hostUserId(listing.getHostUser().getId())
                .hostUserName(listing.getHostUser().getFullName())
                .listingType(listing.getListingType())
                .roomType(listing.getRoomType())
                .furnishingStatus(listing.getFurnishingStatus())
                .rentAmount(listing.getRentAmount())
                .depositAmount(listing.getDepositAmount())
                .localityName(listing.getLocalityName())
                .city(listing.getCity())
                .isAvailable(listing.getIsAvailable())
                .photoUrls(listing.getPhotoUrls())
                .createdAt(listing.getCreatedAt())
                .build();
    }
}
