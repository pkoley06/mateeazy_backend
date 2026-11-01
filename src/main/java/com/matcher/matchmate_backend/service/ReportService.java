package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.ReportCreateRequest;
import com.matcher.matchmate_backend.dto.response.ReportResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.Report;
import com.matcher.matchmate_backend.model.entity.UserBlock;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.ReportRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserBlockRepository blockRepository;
    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;

    public ReportService(
            ReportRepository reportRepository,
            UserBlockRepository blockRepository,
            AppUserRepository userRepository,
            ListingRepository listingRepository
    ) {
        this.reportRepository = reportRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional
    public ReportResponse createReport(UUID reporterId, ReportCreateRequest request) {
        AppUser reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporter not found"));

        AppUser reportedUser = null;
        if (request.getReportedUserId() != null) {
            reportedUser = userRepository.findById(request.getReportedUserId()).orElse(null);
        }

        Listing reportedListing = null;
        if (request.getReportedListingId() != null) {
            reportedListing = listingRepository.findById(request.getReportedListingId()).orElse(null);
            if (reportedListing != null && reportedUser == null) {
                reportedUser = reportedListing.getHostUser();
            }
        }

        if (request.getReportType() == ReportType.USER && reportedUser == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reported user must be specified for user report");
        }
        if (request.getReportType() == ReportType.LISTING && reportedListing == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reported listing must be specified for listing report");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .reportedUser(reportedUser)
                .reportedListing(reportedListing)
                .reportType(request.getReportType())
                .reason(request.getReason())
                .details(request.getDetails())
                .status(ReportStatus.PENDING)
                .build();

        return toResponse(reportRepository.save(report));
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot block yourself");
        }
        if (!userRepository.existsById(blockedId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User to block not found");
        }

        if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            blockRepository.save(UserBlock.builder()
                    .blockerId(blockerId)
                    .blockedId(blockedId)
                    .build());
        }
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public Set<UUID> getBlockedUserIds(UUID blockerId) {
        return blockRepository.findBlockedUserIdsByBlockerId(blockerId);
    }

    public boolean isUserBlocked(UUID blockerId, UUID blockedId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public ReportResponse toResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .reporterId(report.getReporter().getId())
                .reporterName(report.getReporter().getFullName())
                .reportedUserId(report.getReportedUser() != null ? report.getReportedUser().getId() : null)
                .reportedUserName(report.getReportedUser() != null ? report.getReportedUser().getFullName() : null)
                .reportedListingId(report.getReportedListing() != null ? report.getReportedListing().getId() : null)
                .reportedListingTitle(report.getReportedListing() != null ? report.getReportedListing().getTitle() : null)
                .reportType(report.getReportType())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .actionTaken(report.getActionTaken())
                .createdAt(report.getCreatedAt())
                .resolvedAt(report.getResolvedAt())
                .build();
    }
}
