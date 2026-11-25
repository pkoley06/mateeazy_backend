package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.ReportCreateRequest;
import com.matcher.matchmate_backend.dto.response.BlockedUserResponse;
import com.matcher.matchmate_backend.dto.response.ReportResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.CommunityComment;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.Report;
import com.matcher.matchmate_backend.model.entity.UserBlock;
import com.matcher.matchmate_backend.model.entity.UserStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.CommunityCommentRepository;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.ReportRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import com.matcher.matchmate_backend.repository.UserStatusRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserBlockRepository blockRepository;
    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final UserStatusRepository userStatusRepository;

    public ReportService(
            ReportRepository reportRepository,
            UserBlockRepository blockRepository,
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            CommunityPostRepository communityPostRepository,
            CommunityCommentRepository communityCommentRepository,
            UserStatusRepository userStatusRepository
    ) {
        this.reportRepository = reportRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.communityPostRepository = communityPostRepository;
        this.communityCommentRepository = communityCommentRepository;
        this.userStatusRepository = userStatusRepository;
    }

    @Transactional
    public ReportResponse createReport(UUID reporterId, ReportCreateRequest request) {
        AppUser reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporter not found"));

        switch (request.getReportType()) {
            case POST -> {
                return reportPost(reporter, request);
            }
            case COMMENT -> {
                return reportComment(reporter, request);
            }
            case STATUS -> {
                return reportStatus(reporter, request);
            }
            default -> {
                // USER and LISTING continue below.
            }
        }

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

    private ReportResponse reportPost(AppUser reporter, ReportCreateRequest request) {
        CommunityPost post = Optional.ofNullable(request.getReportedPostId())
                .flatMap(communityPostRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        AppUser author = post.getAuthor();
        refuseSelfReport(reporter, author);

        // Reporting the same thing twice while the first report is open just returns it.
        Optional<Report> existing = reportRepository.findFirstByReporterIdAndReportedPostIdAndStatus(
                reporter.getId(), post.getId(), ReportStatus.PENDING);
        if (existing.isPresent()) return toResponse(existing.get());

        String media = post.getImageUrls() != null && !post.getImageUrls().isEmpty()
                ? post.getImageUrls().get(0) : null;
        return toResponse(reportRepository.save(Report.builder()
                .reporter(reporter)
                .reportedUser(author)
                .reportedPost(post)
                .contextPostId(post.getId())
                .contentPreview(joinText(post.getTitle(), post.getContent()))
                .contentMediaUrl(media)
                .reportType(ReportType.POST)
                .reason(request.getReason())
                .details(request.getDetails())
                .status(ReportStatus.PENDING)
                .build()));
    }

    private ReportResponse reportComment(AppUser reporter, ReportCreateRequest request) {
        CommunityComment comment = Optional.ofNullable(request.getReportedCommentId())
                .flatMap(communityCommentRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        AppUser author = comment.getUser();
        refuseSelfReport(reporter, author);

        Optional<Report> existing = reportRepository.findFirstByReporterIdAndReportedCommentIdAndStatus(
                reporter.getId(), comment.getId(), ReportStatus.PENDING);
        if (existing.isPresent()) return toResponse(existing.get());

        return toResponse(reportRepository.save(Report.builder()
                .reporter(reporter)
                .reportedUser(author)
                .reportedComment(comment)
                .contextPostId(comment.getPost().getId())
                .contentPreview(comment.getContent())
                .reportType(ReportType.COMMENT)
                .reason(request.getReason())
                .details(request.getDetails())
                .status(ReportStatus.PENDING)
                .build()));
    }

    private ReportResponse reportStatus(AppUser reporter, ReportCreateRequest request) {
        UserStatus status = Optional.ofNullable(request.getReportedStatusId())
                .flatMap(userStatusRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status not found"));
        AppUser author = status.getUser();
        refuseSelfReport(reporter, author);

        Optional<Report> existing = reportRepository.findFirstByReporterIdAndReportedStatusIdAndStatus(
                reporter.getId(), status.getId(), ReportStatus.PENDING);
        if (existing.isPresent()) return toResponse(existing.get());

        return toResponse(reportRepository.save(Report.builder()
                .reporter(reporter)
                .reportedUser(author)
                .reportedStatus(status)
                .contentPreview(status.getCaption())
                .contentMediaUrl(status.getMediaUrl())
                .reportType(ReportType.STATUS)
                .reason(request.getReason())
                .details(request.getDetails())
                .status(ReportStatus.PENDING)
                .build()));
    }

    private static void refuseSelfReport(AppUser reporter, AppUser author) {
        if (author != null && reporter.getId().equals(author.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot report your own content");
        }
    }

    static String joinText(String title, String body) {
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasBody = body != null && !body.isBlank();
        if (hasTitle && hasBody) return title.trim() + "\n\n" + body.trim();
        if (hasTitle) return title.trim();
        return hasBody ? body.trim() : null;
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

    public List<BlockedUserResponse> getBlockedUserDetails(UUID blockerId) {
        List<UserBlock> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId);
        Map<UUID, AppUser> users = new HashMap<>();
        for (AppUser u : userRepository.findAllById(blocks.stream().map(UserBlock::getBlockedId).toList())) {
            users.put(u.getId(), u);
        }
        return blocks.stream()
                .filter(b -> users.containsKey(b.getBlockedId()))
                .map(b -> BlockedUserResponse.builder()
                        .userId(b.getBlockedId())
                        .fullName(users.get(b.getBlockedId()).getFullName())
                        .avatarUrl(users.get(b.getBlockedId()).getAvatarUrl())
                        .blockedAt(b.getCreatedAt())
                        .build())
                .toList();
    }

    /** True when either user has blocked the other — messaging between them must be refused. */
    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return blockRepository.existsBlockBetween(a, b);
    }

    public boolean isUserBlocked(UUID blockerId, UUID blockedId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /** Whether the reported post, comment or status still exists; null for user and room reports. */
    private static Boolean isContentAvailable(Report report) {
        return switch (report.getReportType()) {
            case POST -> report.getReportedPost() != null;
            case COMMENT -> report.getReportedComment() != null;
            case STATUS -> report.getReportedStatus() != null;
            default -> null;
        };
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
                .reportedPostId(report.getReportedPost() != null ? report.getReportedPost().getId() : null)
                .reportedPostTitle(report.getReportedPost() != null ? report.getReportedPost().getTitle() : null)
                .reportedCommentId(report.getReportedComment() != null ? report.getReportedComment().getId() : null)
                .reportedStatusId(report.getReportedStatus() != null ? report.getReportedStatus().getId() : null)
                .contextPostId(report.getContextPostId())
                .contentPreview(report.getContentPreview())
                .contentMediaUrl(report.getContentMediaUrl())
                .contentAvailable(isContentAvailable(report))
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
