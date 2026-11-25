package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.AdminReportResolveRequest;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.dto.response.ReportResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.Report;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ModerationAction;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.ReportRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final ReportService reportService;
    private final AdminCommunityService adminCommunityService;

    public AdminReportService(
            ReportRepository reportRepository,
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            ReportService reportService,
            AdminCommunityService adminCommunityService
    ) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.reportService = reportService;
        this.adminCommunityService = adminCommunityService;
    }

    @Transactional
    public PageResponse<ReportResponse> getReports(ReportStatus status, ReportType type, int page, int size) {
        Page<Report> result = reportRepository.searchReports(status, type, PageRequest.of(page, size));
        return PageResponse.<ReportResponse>builder()
                .content(result.getContent().stream().map(reportService::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .isLast(result.isLast())
                .build();
    }

    @Transactional
    public ReportResponse resolveReport(UUID reportId, AdminReportResolveRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        ModerationAction action = request.getAction();
        switch (report.getReportType()) {
            case POST, COMMENT, STATUS -> {
                return resolveContentReport(report, request);
            }
            default -> {
                // User and room reports below.
            }
        }
        report.setActionTaken(action);
        report.setResolvedAt(OffsetDateTime.now());

        if (action == ModerationAction.USER_SUSPENDED && report.getReportedUser() != null) {
            AppUser user = report.getReportedUser();
            user.setIsSuspended(true);
            user.setSuspendedReason(request.getNotes() != null ? request.getNotes() : "Suspended via report #" + reportId);
            userRepository.save(user);
            report.setStatus(ReportStatus.RESOLVED);
        } else if (action == ModerationAction.LISTING_DELISTED && report.getReportedListing() != null) {
            Listing listing = report.getReportedListing();
            listing.setIsAvailable(false);
            listingRepository.save(listing);
            report.setStatus(ReportStatus.RESOLVED);
        } else if (action == ModerationAction.LISTING_DELETED && report.getReportedListing() != null) {
            listingRepository.delete(report.getReportedListing());
            report.setReportedListing(null);
            report.setStatus(ReportStatus.RESOLVED);
        } else if (action == ModerationAction.DISMISSED) {
            report.setStatus(ReportStatus.DISMISSED);
        } else {
            report.setStatus(ReportStatus.RESOLVED);
        }

        return reportService.toResponse(reportRepository.save(report));
    }

    /**
     * Community content: dismissing or removing applies to every open report on the
     * same post, comment or status, so one decision clears them all.
     */
    private ReportResponse resolveContentReport(Report report, AdminReportResolveRequest request) {
        ModerationAction action = request.getAction();
        UUID postId = report.getReportedPost() != null ? report.getReportedPost().getId() : null;
        UUID commentId = report.getReportedComment() != null ? report.getReportedComment().getId() : null;
        UUID statusId = report.getReportedStatus() != null ? report.getReportedStatus().getId() : null;

        if (action == ModerationAction.DISMISSED) {
            if (postId != null) adminCommunityService.dismissPostReports(postId);
            else if (commentId != null) adminCommunityService.dismissCommentReports(commentId);
            else if (statusId != null) adminCommunityService.dismissStatusReports(statusId);
        } else if (action == ModerationAction.CONTENT_REMOVED) {
            if (postId != null) adminCommunityService.setPostStatus(postId, CommunityPostStatus.ARCHIVED);
            else if (commentId != null) adminCommunityService.deleteComment(commentId);
            else if (statusId != null) adminCommunityService.deleteStatus(statusId);
        } else if (action == ModerationAction.USER_SUSPENDED && report.getReportedUser() != null) {
            AppUser user = report.getReportedUser();
            user.setIsSuspended(true);
            user.setSuspendedReason(request.getNotes() != null
                    ? request.getNotes() : "Suspended via report #" + report.getId());
            userRepository.save(user);
        }

        // The content may already be gone (or the action touched only the author):
        // make sure this report itself is closed.
        if (report.getStatus() == ReportStatus.PENDING) {
            report.setActionTaken(action);
            report.setStatus(action == ModerationAction.DISMISSED ? ReportStatus.DISMISSED : ReportStatus.RESOLVED);
            report.setResolvedAt(OffsetDateTime.now());
        }
        return reportService.toResponse(reportRepository.save(report));
    }
}
