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

@Repository
public interface ListingLikeRepository extends JpaRepository<ListingLike, ListingLike.ListingLikeId> {

    boolean existsByUserIdAndListingId(Long userId, Long listingId);

    Optional<ListingLike> findByUserIdAndListingId(Long userId, Long listingId);

    long countByListingId(Long listingId);

    @Query("SELECT l.listing FROM ListingLike l WHERE l.userId = :userId ORDER BY l.createdAt DESC")
    List<Listing> findLikedListingsByUserId(@Param("userId") Long userId);

    /** AccountPurgeService: likes the user placed on OTHER users' listings. */
    @Modifying
    @Query("DELETE FROM ListingLike l WHERE l.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
