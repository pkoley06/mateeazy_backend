package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingCommentLikeRepository;
import com.matcher.matchmate_backend.repository.ListingCommentRepository;
import com.matcher.matchmate_backend.repository.ListingLikeRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import com.matcher.matchmate_backend.storage.StorageService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Runs the actual grace-period purge for one user, called by {@link AccountPurgeScheduler} once
 * {@code scheduledPurgeAt} has lapsed. "Purge" here means reset-in-place, not a hard delete: the
 * {@code AppUser} row and its Firebase login survive, blanked back to exactly the state
 * {@link AuthService#autoProvisionUser} gives a brand-new signup, so signing in again just walks
 * through onboarding like a first-time user. This sidesteps FK-cascade risk across listings,
 * comments, likes and blocks that a true hard delete of the row would carry.
 */
@Service
public class AccountPurgeService {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeService.class);

    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final ListingCommentRepository listingCommentRepository;
    private final ListingLikeRepository listingLikeRepository;
    private final ListingCommentLikeRepository listingCommentLikeRepository;
    private final UserBlockRepository userBlockRepository;
    private final StorageService storageService;

    public AccountPurgeService(
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            ListingCommentRepository listingCommentRepository,
            ListingLikeRepository listingLikeRepository,
            ListingCommentLikeRepository listingCommentLikeRepository,
            UserBlockRepository userBlockRepository,
            StorageService storageService
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.listingCommentRepository = listingCommentRepository;
        this.listingLikeRepository = listingLikeRepository;
        this.listingCommentLikeRepository = listingCommentLikeRepository;
        this.userBlockRepository = userBlockRepository;
        this.storageService = storageService;
    }

    /** Its own transaction per user, called from a different bean (the scheduler) so this method's @Transactional actually applies. */
    @Transactional
    public void purgeUser(UUID userId) {
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.PENDING_DELETION) {
            return; // already cancelled/changed by a race - no-op, not an error
        }

        // Suspension always wins: a suspended user's data is never purged or reset by this job,
        // even if the grace period has lapsed. It stays parked until an admin lifts the
        // suspension, so a suspension can never be laundered by deleting and waiting it out.
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            log.info("Skipping purge of suspended account {} - suspension survives the grace period", userId);
            return;
        }

        // 1. Delete listing photos from storage, then the listings themselves - this cascades
        //    DB-side (ON DELETE CASCADE) to that listing's likes, comments, comment-likes and
        //    any moderation reports filed against it.
        List<Listing> listings = listingRepository.findByHostUserId(userId);
        for (Listing listing : listings) {
            for (String url : listing.getPhotoUrls()) {
                if (storageService.isManagedUrl(url)) storageService.delete(url);
            }
        }
        listingRepository.deleteAll(listings);

        // 2. The user's own avatar, before the column is wiped below.
        if (user.getAvatarUrl() != null && storageService.isManagedUrl(user.getAvatarUrl())) {
            storageService.delete(user.getAvatarUrl());
        }

        // 3. Content not reachable via the listing cascade above: the user's own comments/likes
        //    on OTHER users' listings, and block relationships (as blocker or blocked).
        listingCommentRepository.deleteAllByUserId(userId);
        listingLikeRepository.deleteAllByUserId(userId);
        listingCommentLikeRepository.deleteAllByUserId(userId);
        userBlockRepository.deleteAllInvolvingUser(userId);

        // 4. Reset the row to the exact blank slate AuthService.autoProvisionUser uses.
        user.setFullName("New Roommate");
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setDateOfBirth(LocalDate.of(2000, 1, 1));
        user.setGender(Gender.OTHERS);
        user.setOccupationCategory(null);
        user.setOccupationTitle(null);
        user.setSmokingHabit(null);
        user.setDietaryPreference(null);
        user.setSleepSchedule(null);
        user.setCleanlinessLevel(null);
        user.setGuestTolerance(null);
        user.setLastKnownLocation(null);
        user.setLastKnownLocality("Kolkata");
        user.setTargetSearchLocation(null);
        user.setTargetSearchLocality(null);
        user.setSearchRadiusKm(10.0);
        user.setIsLookingForRoom(true);
        user.setOnboardingCompleted(false);
        user.setFcmToken(null);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setIsActive(true);
        user.setDisabledAt(null);
        user.setDeletionRequestedAt(null);
        user.setScheduledPurgeAt(null);
        userRepository.save(user);

        log.info("Purged (reset-in-place) account {}", userId);
    }
}
