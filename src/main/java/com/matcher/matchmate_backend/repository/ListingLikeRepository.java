package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.ListingLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingLikeRepository extends JpaRepository<ListingLike, ListingLike.ListingLikeId> {

    boolean existsByUserIdAndListingId(UUID userId, UUID listingId);

    Optional<ListingLike> findByUserIdAndListingId(UUID userId, UUID listingId);

    long countByListingId(UUID listingId);

    /** Which of {@code listingIds} the user has liked - one query for a whole feed page. */
    @Query("SELECT l.listingId FROM ListingLike l WHERE l.userId = :userId AND l.listingId IN :listingIds")
    List<UUID> findLikedListingIds(@Param("userId") UUID userId, @Param("listingIds") List<UUID> listingIds);

    @Query("SELECT l.listing FROM ListingLike l WHERE l.userId = :userId ORDER BY l.createdAt DESC")
    List<Listing> findLikedListingsByUserId(@Param("userId") UUID userId);

    /** AccountPurgeService: likes the user placed on OTHER users' listings. */
    @Modifying
    @Query("DELETE FROM ListingLike l WHERE l.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
