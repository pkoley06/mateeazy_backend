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

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query("SELECT r FROM Report r " +
            "LEFT JOIN FETCH r.reporter " +
            "LEFT JOIN FETCH r.reportedUser " +
            "LEFT JOIN FETCH r.reportedListing " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:reportType IS NULL OR r.reportType = :reportType) " +
            "ORDER BY r.createdAt DESC")
    Page<Report> searchReports(
            @Param("status") ReportStatus status,
            @Param("reportType") ReportType reportType,
            Pageable pageable
    );

    long countByStatus(ReportStatus status);
}
