package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.AdminCommunityCommentResponse;
import com.matcher.matchmate_backend.dto.response.AdminCommunityPostDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminCommunityPostResponse;
import com.matcher.matchmate_backend.dto.response.AdminStatusDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminStatusResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.CommunityComment;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.Report;
import com.matcher.matchmate_backend.model.entity.UserStatus;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ModerationAction;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.repository.CommunityCommentLikeRepository;
import com.matcher.matchmate_backend.repository.CommunityCommentRepository;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.ReportRepository;
import com.matcher.matchmate_backend.repository.UserStatusRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin moderation of the Community tab: posts (pin, archive, restore, photos),
 * their comments, and 24-hour statuses, with the reports filed against them.
 *
 * Removing content resolves the open reports on it (action CONTENT_REMOVED), and
 * the reports keep a snapshot of what was said, so the Reports page still makes sense.
 */
@Service
public class AdminCommunityService {

    /** Placeholder for "no filter" in queries whose parameters may not be null. */
    private static final UUID NO_ID = new UUID(0, 0);

    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityCommentLikeRepository commentLikeRepository;
    private final UserStatusRepository statusRepository;
    private final ReportRepository reportRepository;
    private final ListingRepository listingRepository;
    private final CommunityPostService communityPostService;
    private final UserStatusService userStatusService;
    private final ReportService reportService;

    public AdminCommunityService(
            CommunityPostRepository postRepository,
            CommunityCommentRepository commentRepository,
            CommunityCommentLikeRepository commentLikeRepository,
            UserStatusRepository statusRepository,
            ReportRepository reportRepository,
            ListingRepository listingRepository,
            CommunityPostService communityPostService,
            UserStatusService userStatusService,
            ReportService reportService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.statusRepository = statusRepository;
        this.reportRepository = reportRepository;
        this.listingRepository = listingRepository;
        this.communityPostService = communityPostService;
        this.userStatusService = userStatusService;
        this.reportService = reportService;
    }

    // ── Posts ────────────────────────────────────────────────────────────

    /**
     * @param state ALL (default), ACTIVE, HIDDEN (archived or flagged), PINNED or REPORTED.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminCommunityPostResponse> listPosts(
            String search, String categoryStr, String state, UUID authorId, int page, int size) {
        CommunityPostCategory category = parseCategory(categoryStr);
        String s = state == null ? "ALL" : state.trim().toUpperCase();

        Page<CommunityPost> result = postRepository.adminSearch(
                pattern(search),
                category == null,
                category == null ? CommunityPostCategory.GENERAL : category,
                authorId == null,
                authorId == null ? NO_ID : authorId,
                s.equals("ACTIVE"),
                s.equals("HIDDEN") || s.equals("ARCHIVED"),
                s.equals("PINNED"),
                s.equals("REPORTED"),
                CommunityPostStatus.ACTIVE,
                ReportStatus.PENDING,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));

        Map<UUID, Long> pending = pendingByPost(result.getContent().stream().map(CommunityPost::getId).toList());
        return PageResponse.of(result, p -> toAdminPost(p, pending.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public AdminCommunityPostDetailResponse getPost(UUID id) {
        CommunityPost post = findPost(id);
        List<CommunityComment> comments = commentRepository.findAllForPost(id);
        List<UUID> commentIds = comments.stream().map(CommunityComment::getId).toList();

        Map<UUID, Long> likes = new HashMap<>();
        Map<UUID, Long> commentReports = new HashMap<>();
        if (!commentIds.isEmpty()) {
            for (Object[] row : commentLikeRepository.countByCommentIds(commentIds)) {
                likes.put((UUID) row[0], (Long) row[1]);
            }
            commentReports = toCountMap(reportRepository.countByCommentIds(ReportStatus.PENDING, commentIds));
        }
        Map<UUID, Long> commentReportsFinal = commentReports;
        UUID postAuthorId = post.getAuthor().getId();
        List<AdminCommunityCommentResponse> thread = threadOrder(comments).stream()
                .map(c -> toAdminComment(c, post, postAuthorId,
                        likes.getOrDefault(c.getId(), 0L),
                        commentReportsFinal.getOrDefault(c.getId(), 0L)))
                .toList();

        AppUser author = post.getAuthor();
        OffsetDateTime now = OffsetDateTime.now();
        long pending = pendingByPost(List.of(id)).getOrDefault(id, 0L);
        return AdminCommunityPostDetailResponse.builder()
                .post(toAdminPost(post, pending))
                .comments(thread)
                .reports(reportRepository.findAllForPost(id).stream().map(reportService::toResponse).toList())
                .author(AdminCommunityPostDetailResponse.AuthorStats.builder()
                        .postsCount(postRepository.countByAuthorId(author.getId()))
                        .liveStatuses(statusRepository.countByUserIdAndExpiresAtAfter(author.getId(), now))
                        .listingsCount(listingRepository.countByHostUserId(author.getId()))
                        .suspended(Boolean.TRUE.equals(author.getIsSuspended()))
                        .joinedAt(author.getCreatedAt())
                        .build())
                .build();
    }

    @Transactional
    public AdminCommunityPostResponse setPinned(UUID id, boolean pinned) {
        CommunityPost post = findPost(id);
        post.setIsPinned(pinned);
        return toAdminPost(postRepository.save(post), pendingByPost(List.of(id)).getOrDefault(id, 0L));
    }

    /** ARCHIVED hides the post in the app and resolves its open reports; ACTIVE restores it. */
    @Transactional
    public AdminCommunityPostResponse setPostStatus(UUID id, CommunityPostStatus status) {
        CommunityPost post = findPost(id);
        post.setStatus(status);
        if (status != CommunityPostStatus.ACTIVE) {
            resolve(reportRepository.findByStatusAndPost(ReportStatus.PENDING, id), ModerationAction.CONTENT_REMOVED);
        }
        return toAdminPost(postRepository.save(post), pendingByPost(List.of(id)).getOrDefault(id, 0L));
    }

    /** Kept for the old admin build: "delete" a post = archive it. */
    @Transactional
    public void deletePost(UUID id) {
        setPostStatus(id, CommunityPostStatus.ARCHIVED);
    }

    @Transactional
    public AdminCommunityPostResponse removePhoto(UUID id, String url) {
        CommunityPost post = findPost(id);
        List<String> photos = new ArrayList<>(post.getImageUrls() == null ? List.of() : post.getImageUrls());
        if (!photos.remove(url)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That photo is not on this post");
        }
        post.setImageUrls(photos);
        return toAdminPost(postRepository.save(post), pendingByPost(List.of(id)).getOrDefault(id, 0L));
    }

    /** "Looks fine": dismiss the open reports on the post itself (not those on its comments). */
    @Transactional
    public void dismissPostReports(UUID id) {
        findPost(id);
        resolve(reportRepository.findByStatusAndPost(ReportStatus.PENDING, id), ModerationAction.DISMISSED);
    }

    // ── Comments ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AdminCommunityCommentResponse> listComments(String search, boolean reportedOnly, int page, int size) {
        Page<CommunityComment> result = commentRepository.adminSearch(
                pattern(search), reportedOnly, ReportStatus.PENDING,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
        List<UUID> ids = result.getContent().stream().map(CommunityComment::getId).toList();
        Map<UUID, Long> likes = new HashMap<>();
        Map<UUID, Long> reports = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : commentLikeRepository.countByCommentIds(ids)) {
                likes.put((UUID) row[0], (Long) row[1]);
            }
            reports = toCountMap(reportRepository.countByCommentIds(ReportStatus.PENDING, ids));
        }
        Map<UUID, Long> reportsFinal = reports;
        return PageResponse.of(result, c -> toAdminComment(c, c.getPost(), c.getPost().getAuthor().getId(),
                likes.getOrDefault(c.getId(), 0L), reportsFinal.getOrDefault(c.getId(), 0L)));
    }

    /** Deletes a comment and its replies, resolving the open reports on all of them. */
    @Transactional
    public void deleteComment(UUID id) {
        CommunityComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        List<UUID> removedIds = new ArrayList<>(commentRepository.findDescendantIds(id));
        removedIds.add(id);

        for (Report report : reportRepository.findByCommentIds(removedIds)) {
            if (report.getStatus() == ReportStatus.PENDING) {
                markResolved(report, ModerationAction.CONTENT_REMOVED);
            }
            report.setReportedComment(null);
        }
        reportRepository.flush();

        UUID postId = comment.getPost().getId();
        commentRepository.delete(comment); // replies go with it (ON DELETE CASCADE)
        postRepository.updateCommentsCount(postId, -removedIds.size());
    }

    @Transactional
    public void dismissCommentReports(UUID id) {
        if (!commentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        resolve(reportRepository.findByStatusAndComment(ReportStatus.PENDING, id), ModerationAction.DISMISSED);
    }

    // ── Statuses ─────────────────────────────────────────────────────────

    /**
     * @param state LIVE (default), EXPIRED, REPORTED or ALL.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminStatusResponse> listStatuses(String state, String search, UUID userId, int page, int size) {
        String s = state == null ? "LIVE" : state.trim().toUpperCase();
        OffsetDateTime now = OffsetDateTime.now();
        Page<UserStatus> result = statusRepository.adminSearch(
                pattern(search),
                userId == null,
                userId == null ? NO_ID : userId,
                s.equals("LIVE"),
                s.equals("EXPIRED"),
                s.equals("REPORTED"),
                now,
                ReportStatus.PENDING,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));

        List<UUID> ids = result.getContent().stream().map(UserStatus::getId).toList();
        Map<UUID, Long> pending = ids.isEmpty()
                ? Map.of()
                : toCountMap(reportRepository.countByStatusIds(ReportStatus.PENDING, ids));
        return PageResponse.of(result, st -> toAdminStatus(st, pending.getOrDefault(st.getId(), 0L), now));
    }

    @Transactional(readOnly = true)
    public AdminStatusDetailResponse getStatus(UUID id) {
        UserStatus status = findStatus(id);
        List<Report> reports = reportRepository.findAllForStatus(id);
        long pending = reports.stream().filter(r -> r.getStatus() == ReportStatus.PENDING).count();
        return AdminStatusDetailResponse.builder()
                .status(toAdminStatus(status, pending, OffsetDateTime.now()))
                .reports(reports.stream().map(reportService::toResponse).toList())
                .build();
    }

    /** Removes a status for everyone and resolves its open reports. */
    @Transactional
    public void deleteStatus(UUID id) {
        UserStatus status = findStatus(id);
        for (Report report : reportRepository.findByUserStatusId(id)) {
            if (report.getStatus() == ReportStatus.PENDING) {
                markResolved(report, ModerationAction.CONTENT_REMOVED);
            }
            report.setReportedStatus(null);
        }
        reportRepository.flush();
        statusRepository.delete(status);
    }

    @Transactional
    public void dismissStatusReports(UUID id) {
        findStatus(id);
        resolve(reportRepository.findByStatusAndUserStatus(ReportStatus.PENDING, id), ModerationAction.DISMISSED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Open report counts per post, counting reports on the post and on its comments. */
    Map<UUID, Long> pendingByPost(Collection<UUID> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return toCountMap(reportRepository.countByPostIds(ReportStatus.PENDING, postIds));
    }

    AdminCommunityPostResponse toAdminPost(CommunityPost post, long pendingReports) {
        return AdminCommunityPostResponse.builder()
                .post(communityPostService.toPostResponse(post, false, null))
                .pendingReports(pendingReports)
                .build();
    }

    private AdminStatusResponse toAdminStatus(UserStatus status, long pendingReports, OffsetDateTime now) {
        return AdminStatusResponse.builder()
                .status(userStatusService.toStatusResponse(status, null))
                .pendingReports(pendingReports)
                .live(status.getExpiresAt() != null && status.getExpiresAt().isAfter(now))
                .build();
    }

    private static AdminCommunityCommentResponse toAdminComment(
            CommunityComment c, CommunityPost post, UUID postAuthorId, long likes, long pendingReports) {
        AppUser author = c.getUser();
        CommunityComment parent = c.getParent();
        return AdminCommunityCommentResponse.builder()
                .id(c.getId())
                .postId(post.getId())
                .postTitle(post.getTitle())
                .postStatus(post.getStatus())
                .authorId(author.getId())
                .authorName(author.getFullName())
                .authorAvatarUrl(author.getAvatarUrl())
                .byPostAuthor(author.getId().equals(postAuthorId))
                .content(c.getContent())
                .parentId(parent != null ? parent.getId() : null)
                .depth(c.getDepth())
                .replyToName(parent != null ? parent.getUser().getFullName() : null)
                .likesCount(likes)
                .pendingReports(pendingReports)
                .createdAt(c.getCreatedAt())
                .build();
    }

    /**
     * Orders comments as a thread: every top-level comment (oldest first) followed by
     * its replies, depth-first. Replies whose parent is missing are kept at the end.
     */
    static List<CommunityComment> threadOrder(List<CommunityComment> comments) {
        Map<UUID, List<CommunityComment>> children = new LinkedHashMap<>();
        List<CommunityComment> roots = new ArrayList<>();
        Map<UUID, CommunityComment> byId = new HashMap<>();
        for (CommunityComment c : comments) byId.put(c.getId(), c);
        for (CommunityComment c : comments) {
            CommunityComment parent = c.getParent();
            if (parent == null || !byId.containsKey(parent.getId())) {
                roots.add(c);
            } else {
                children.computeIfAbsent(parent.getId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<CommunityComment> out = new ArrayList<>(comments.size());
        for (CommunityComment root : roots) appendWithReplies(root, children, out);
        return out;
    }

    private static void appendWithReplies(
            CommunityComment c, Map<UUID, List<CommunityComment>> children, List<CommunityComment> out) {
        out.add(c);
        for (CommunityComment reply : children.getOrDefault(c.getId(), List.of())) {
            appendWithReplies(reply, children, out);
        }
    }

    private void resolve(List<Report> reports, ModerationAction action) {
        for (Report report : reports) markResolved(report, action);
        reportRepository.saveAll(reports);
    }

    private static void markResolved(Report report, ModerationAction action) {
        report.setActionTaken(action);
        report.setStatus(action == ModerationAction.DISMISSED ? ReportStatus.DISMISSED : ReportStatus.RESOLVED);
        report.setResolvedAt(OffsetDateTime.now());
    }

    private CommunityPost findPost(UUID id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private UserStatus findStatus(UUID id) {
        return statusRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status not found"));
    }

    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> out = new HashMap<>();
        for (Object[] row : rows) out.put((UUID) row[0], (Long) row[1]);
        return out;
    }

    private static String pattern(String search) {
        return StringUtils.hasText(search) ? "%" + search.trim().toLowerCase() + "%" : "%";
    }

    private static CommunityPostCategory parseCategory(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            CommunityPostCategory c = CommunityPostCategory.valueOf(raw.trim().toUpperCase());
            return c == CommunityPostCategory.ALL ? null : c;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
