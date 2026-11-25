package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Report;
import com.matcher.matchmate_backend.model.enums.moderation.ReportStatus;
import com.matcher.matchmate_backend.model.enums.moderation.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query(value = "SELECT r FROM Report r " +
            "LEFT JOIN FETCH r.reporter " +
            "LEFT JOIN FETCH r.reportedUser " +
            "LEFT JOIN FETCH r.reportedListing " +
            "LEFT JOIN FETCH r.reportedPost " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:reportType IS NULL OR r.reportType = :reportType) " +
            "ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM Report r " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:reportType IS NULL OR r.reportType = :reportType)")
    Page<Report> searchReports(
            @Param("status") ReportStatus status,
            @Param("reportType") ReportType reportType,
            Pageable pageable
    );

    long countByStatus(ReportStatus status);

    long countByStatusAndCreatedAtAfter(ReportStatus status, OffsetDateTime after);

    /** Every report about a post: the post itself and any comment on it. Newest first. */
    @Query("SELECT r FROM Report r LEFT JOIN FETCH r.reporter " +
            "WHERE r.contextPostId = :postId " +
            "ORDER BY r.createdAt DESC")
    List<Report> findAllForPost(@Param("postId") UUID postId);

    @Query("SELECT r FROM Report r LEFT JOIN FETCH r.reporter " +
            "WHERE r.reportedStatus.id = :statusId ORDER BY r.createdAt DESC")
    List<Report> findAllForStatus(@Param("statusId") UUID statusId);

    @Query("SELECT r FROM Report r WHERE r.status = :status AND r.reportedPost.id = :postId")
    List<Report> findByStatusAndPost(@Param("status") ReportStatus status, @Param("postId") UUID postId);

    @Query("SELECT r FROM Report r WHERE r.status = :status AND r.reportedComment.id = :commentId")
    List<Report> findByStatusAndComment(@Param("status") ReportStatus status, @Param("commentId") UUID commentId);

    @Query("SELECT r FROM Report r WHERE r.status = :status AND r.reportedStatus.id = :statusId")
    List<Report> findByStatusAndUserStatus(@Param("status") ReportStatus status, @Param("statusId") UUID statusId);

    /** Reports pointing at any of these comments, whatever their state (to unlink before deleting). */
    @Query("SELECT r FROM Report r WHERE r.reportedComment.id IN :commentIds")
    List<Report> findByCommentIds(@Param("commentIds") Collection<UUID> commentIds);

    @Query("SELECT r FROM Report r WHERE r.reportedStatus.id = :statusId")
    List<Report> findByUserStatusId(@Param("statusId") UUID statusId);

    /** Rows of [postId, pending report count], counting reports on the post and on its comments. */
    @Query("SELECT r.contextPostId, COUNT(r) FROM Report r " +
            "WHERE r.status = :status AND r.contextPostId IN :postIds GROUP BY r.contextPostId")
    List<Object[]> countByPostIds(@Param("status") ReportStatus status, @Param("postIds") Collection<UUID> postIds);

    @Query("SELECT r.reportedComment.id, COUNT(r) FROM Report r " +
            "WHERE r.status = :status AND r.reportedComment.id IN :commentIds GROUP BY r.reportedComment.id")
    List<Object[]> countByCommentIds(@Param("status") ReportStatus status, @Param("commentIds") Collection<UUID> commentIds);

    @Query("SELECT r.reportedStatus.id, COUNT(r) FROM Report r " +
            "WHERE r.status = :status AND r.reportedStatus.id IN :statusIds GROUP BY r.reportedStatus.id")
    List<Object[]> countByStatusIds(@Param("status") ReportStatus status, @Param("statusIds") Collection<UUID> statusIds);

    java.util.Optional<Report> findFirstByReporterIdAndReportedPostIdAndStatus(UUID reporterId, UUID postId, ReportStatus status);

    java.util.Optional<Report> findFirstByReporterIdAndReportedCommentIdAndStatus(UUID reporterId, UUID commentId, ReportStatus status);

    java.util.Optional<Report> findFirstByReporterIdAndReportedStatusIdAndStatus(UUID reporterId, UUID statusId, ReportStatus status);
}
