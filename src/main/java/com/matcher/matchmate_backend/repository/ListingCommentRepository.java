package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.ListingComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingCommentRepository extends JpaRepository<ListingComment, UUID> {

    @Query("SELECT c FROM ListingComment c JOIN FETCH c.user WHERE c.listing.id = :listingId ORDER BY c.createdAt ASC")
    List<ListingComment> findThread(@Param("listingId") UUID listingId);

    @Query("SELECT c FROM ListingComment c JOIN FETCH c.user JOIN FETCH c.listing l JOIN FETCH l.hostUser WHERE c.id = :id")
    Optional<ListingComment> findWithRelations(@Param("id") UUID id);

    long countByListingId(UUID listingId);

    /**
     * Admin moderation log: newest first, optional keyword over the body, author name and
     * room title, optional single-room filter. {@code :search} is cast for the same reason
     * as {@link ListingRepository#searchAdminListings}.
     */
    @Query(value = "SELECT c FROM ListingComment c JOIN FETCH c.user u JOIN FETCH c.listing l WHERE " +
            "(:listingId IS NULL OR l.id = :listingId) AND " +
            "(:search IS NULL OR LOWER(c.content) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
            "  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
            "  OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
            "ORDER BY c.createdAt DESC",
            countQuery = "SELECT COUNT(c) FROM ListingComment c JOIN c.user u JOIN c.listing l WHERE " +
            "(:listingId IS NULL OR l.id = :listingId) AND " +
            "(:search IS NULL OR LOWER(c.content) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
            "  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
            "  OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<ListingComment> searchForModeration(
            @Param("search") String search,
            @Param("listingId") UUID listingId,
            Pageable pageable);

    /** AccountPurgeService: comments the user left on OTHER users' listings (own-listing comments already cascade when the listing is deleted). */
    @Modifying
    @Query("DELETE FROM ListingComment c WHERE c.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
