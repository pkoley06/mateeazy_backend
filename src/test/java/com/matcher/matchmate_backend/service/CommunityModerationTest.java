package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.AdminReportResolveRequest;
import com.matcher.matchmate_backend.dto.request.ReportCreateRequest;
import com.matcher.matchmate_backend.dto.response.ReportResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.CommunityComment;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.Report;
import com.matcher.matchmate_backend.model.entity.UserStatus;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ModerationAction;
import com.matcher.matchmate_backend.model.enums.moderation.ReportReason;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.CommunityCommentLikeRepository;
import com.matcher.matchmate_backend.repository.CommunityCommentRepository;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.ReportRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import com.matcher.matchmate_backend.repository.UserStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Community moderation: reports on posts, comments and statuses (with a snapshot of
 * what was said), and admin actions that resolve every open report on the content.
 */
class CommunityModerationTest {

    private final ReportRepository reports = mock(ReportRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CommunityPostRepository posts = mock(CommunityPostRepository.class);
    private final CommunityCommentRepository comments = mock(CommunityCommentRepository.class);
    private final UserStatusRepository statuses = mock(UserStatusRepository.class);

    private ReportService reportService;
    private AdminCommunityService community;
    private AdminReportService adminReports;

    private final AppUser reporter = AppUser.builder().id(UUID.randomUUID()).fullName("Rohan").build();
    private final AppUser author = AppUser.builder().id(UUID.randomUUID()).fullName("Ananya").build();
    private CommunityPost post;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reports, mock(UserBlockRepository.class), users,
                mock(ListingRepository.class), posts, comments, statuses);
        community = new AdminCommunityService(posts, comments, mock(CommunityCommentLikeRepository.class),
                statuses, reports, mock(ListingRepository.class), mock(CommunityPostService.class),
                mock(UserStatusService.class), reportService);
        adminReports = new AdminReportService(reports, users, mock(ListingRepository.class), reportService, community);

        post = CommunityPost.builder()
                .id(UUID.randomUUID())
                .author(author)
                .category(CommunityPostCategory.MARKETPLACE)
                .title("Selling a cooler")
                .content("Barely used")
                .imageUrls(List.of("https://cdn.example/a.jpg", "https://cdn.example/b.jpg"))
                .status(CommunityPostStatus.ACTIVE)
                .build();

        when(users.findById(reporter.getId())).thenReturn(Optional.of(reporter));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(reports.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(posts.save(any(CommunityPost.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReportCreateRequest postReport() {
        return ReportCreateRequest.builder()
                .reportType(ReportType.POST)
                .reportedPostId(post.getId())
                .reason(ReportReason.SCAM)
                .details("Asked for an advance")
                .build();
    }

    @Test
    void reportingAPostKeepsASnapshotAndBlamesTheAuthor() {
        ReportResponse r = reportService.createReport(reporter.getId(), postReport());

        assertThat(r.getReportType()).isEqualTo(ReportType.POST);
        assertThat(r.getReportedUserId()).isEqualTo(author.getId());
        assertThat(r.getReportedPostId()).isEqualTo(post.getId());
        assertThat(r.getContextPostId()).isEqualTo(post.getId());
        assertThat(r.getContentPreview()).isEqualTo("Selling a cooler\n\nBarely used");
        assertThat(r.getContentMediaUrl()).isEqualTo("https://cdn.example/a.jpg");
        assertThat(r.getContentAvailable()).isTrue();
        assertThat(r.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void reportingTheSamePostTwiceReturnsTheOpenReport() {
        Report open = Report.builder().id(UUID.randomUUID()).reporter(reporter).reportedPost(post)
                .reportType(ReportType.POST).reason(ReportReason.SPAM).status(ReportStatus.PENDING).build();
        when(reports.findFirstByReporterIdAndReportedPostIdAndStatus(reporter.getId(), post.getId(), ReportStatus.PENDING))
                .thenReturn(Optional.of(open));

        ReportResponse r = reportService.createReport(reporter.getId(), postReport());

        assertThat(r.getId()).isEqualTo(open.getId());
        verify(reports, never()).save(any(Report.class));
    }

    @Test
    void nobodyCanReportTheirOwnPost() {
        when(users.findById(author.getId())).thenReturn(Optional.of(author));
        assertThatThrownBy(() -> reportService.createReport(author.getId(), postReport()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reportingACommentLinksItToItsPost() {
        CommunityComment comment = CommunityComment.builder().id(UUID.randomUUID()).post(post).user(author)
                .content("DM me on WhatsApp").build();
        when(comments.findById(comment.getId())).thenReturn(Optional.of(comment));

        ReportResponse r = reportService.createReport(reporter.getId(), ReportCreateRequest.builder()
                .reportType(ReportType.COMMENT).reportedCommentId(comment.getId()).reason(ReportReason.SPAM).build());

        assertThat(r.getReportedCommentId()).isEqualTo(comment.getId());
        assertThat(r.getContextPostId()).isEqualTo(post.getId());
        assertThat(r.getContentPreview()).isEqualTo("DM me on WhatsApp");
    }

    @Test
    void reportingAStatusKeepsItsPhoto() {
        UserStatus status = UserStatus.builder().id(UUID.randomUUID()).user(author).caption("Party tonight")
                .mediaUrl("https://cdn.example/s.jpg").expiresAt(OffsetDateTime.now().plusHours(5)).build();
        when(statuses.findById(status.getId())).thenReturn(Optional.of(status));

        ReportResponse r = reportService.createReport(reporter.getId(), ReportCreateRequest.builder()
                .reportType(ReportType.STATUS).reportedStatusId(status.getId()).reason(ReportReason.INAPPROPRIATE).build());

        assertThat(r.getReportedStatusId()).isEqualTo(status.getId());
        assertThat(r.getContentMediaUrl()).isEqualTo("https://cdn.example/s.jpg");
        assertThat(r.getContentPreview()).isEqualTo("Party tonight");
    }

    @Test
    void archivingAPostResolvesItsOpenReports() {
        Report a = pendingPostReport();
        Report b = pendingPostReport();
        when(reports.findByStatusAndPost(ReportStatus.PENDING, post.getId())).thenReturn(List.of(a, b));

        community.setPostStatus(post.getId(), CommunityPostStatus.ARCHIVED);

        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.ARCHIVED);
        for (Report r : List.of(a, b)) {
            assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
            assertThat(r.getActionTaken()).isEqualTo(ModerationAction.CONTENT_REMOVED);
        }
    }

    @Test
    void restoringAPostLeavesReportsAlone() {
        post.setStatus(CommunityPostStatus.ARCHIVED);
        community.setPostStatus(post.getId(), CommunityPostStatus.ACTIVE);

        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.ACTIVE);
        verify(reports, never()).findByStatusAndPost(any(), any());
    }

    @Test
    void removingOnePhotoKeepsTheRest() {
        community.removePhoto(post.getId(), "https://cdn.example/a.jpg");
        assertThat(post.getImageUrls()).containsExactly("https://cdn.example/b.jpg");

        assertThatThrownBy(() -> community.removePhoto(post.getId(), "https://cdn.example/nope.jpg"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deletingACommentTakesItsRepliesAndResolvesTheirReports() {
        CommunityComment comment = CommunityComment.builder().id(UUID.randomUUID()).post(post).user(author)
                .content("spam").build();
        UUID replyId = UUID.randomUUID();
        Report onComment = Report.builder().reportedComment(comment).status(ReportStatus.PENDING)
                .reportType(ReportType.COMMENT).reason(ReportReason.SPAM).reporter(reporter).build();
        Report oldOne = Report.builder().reportedComment(comment).status(ReportStatus.DISMISSED)
                .actionTaken(ModerationAction.DISMISSED)
                .reportType(ReportType.COMMENT).reason(ReportReason.SPAM).reporter(reporter).build();
        when(comments.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(comments.findDescendantIds(comment.getId())).thenReturn(new ArrayList<>(List.of(replyId)));
        when(reports.findByCommentIds(anyList())).thenReturn(List.of(onComment, oldOne));

        community.deleteComment(comment.getId());

        assertThat(onComment.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(onComment.getActionTaken()).isEqualTo(ModerationAction.CONTENT_REMOVED);
        assertThat(onComment.getReportedComment()).isNull();
        assertThat(oldOne.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        verify(comments).delete(comment);
        verify(posts).updateCommentsCount(post.getId(), -2);
    }

    @Test
    void dismissingOneReportDismissesEveryOpenReportOnThatPost() {
        Report clicked = pendingPostReport();
        Report sibling = pendingPostReport();
        when(reports.findById(clicked.getId())).thenReturn(Optional.of(clicked));
        when(reports.findByStatusAndPost(ReportStatus.PENDING, post.getId())).thenReturn(List.of(clicked, sibling));

        ReportResponse r = adminReports.resolveReport(clicked.getId(),
                AdminReportResolveRequest.builder().action(ModerationAction.DISMISSED).build());

        assertThat(r.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(sibling.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.ACTIVE);
    }

    @Test
    void removingFromTheReportsPageArchivesThePost() {
        Report clicked = pendingPostReport();
        when(reports.findById(clicked.getId())).thenReturn(Optional.of(clicked));
        when(reports.findByStatusAndPost(ReportStatus.PENDING, post.getId())).thenReturn(List.of(clicked));

        ReportResponse r = adminReports.resolveReport(clicked.getId(),
                AdminReportResolveRequest.builder().action(ModerationAction.CONTENT_REMOVED).build());

        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.ARCHIVED);
        assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(r.getActionTaken()).isEqualTo(ModerationAction.CONTENT_REMOVED);
    }

    @Test
    void aReportOnContentThatIsAlreadyGoneStillCloses() {
        Report orphan = Report.builder().id(UUID.randomUUID()).reporter(reporter).reportType(ReportType.STATUS)
                .reason(ReportReason.SPAM).status(ReportStatus.PENDING).contentPreview("old status").build();
        when(reports.findById(orphan.getId())).thenReturn(Optional.of(orphan));

        ReportResponse r = adminReports.resolveReport(orphan.getId(),
                AdminReportResolveRequest.builder().action(ModerationAction.CONTENT_REMOVED).build());

        assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(r.getContentAvailable()).isFalse();
        assertThat(r.getContentPreview()).isEqualTo("old status");
        verify(statuses, never()).delete(any());
    }

    @Test
    void threadsListEachCommentBeforeItsReplies() {
        CommunityComment a = comment(null, 1);
        CommunityComment b = comment(null, 2);
        CommunityComment aReply = comment(a, 3);
        CommunityComment aReplyReply = comment(aReply, 4);
        CommunityComment bReply = comment(b, 5);

        List<CommunityComment> ordered = AdminCommunityService.threadOrder(List.of(a, b, aReply, aReplyReply, bReply));

        assertThat(ordered).containsExactly(a, aReply, aReplyReply, b, bReply);
    }

    private Report pendingPostReport() {
        return Report.builder().id(UUID.randomUUID()).reporter(reporter).reportedUser(author).reportedPost(post)
                .contextPostId(post.getId()).reportType(ReportType.POST).reason(ReportReason.SCAM)
                .status(ReportStatus.PENDING).build();
    }

    private CommunityComment comment(CommunityComment parent, int minute) {
        return CommunityComment.builder().id(UUID.randomUUID()).post(post).user(author).parent(parent)
                .content("c" + minute).createdAt(OffsetDateTime.now().plusMinutes(minute)).build();
    }
}
