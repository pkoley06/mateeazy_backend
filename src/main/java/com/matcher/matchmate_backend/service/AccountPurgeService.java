package com.matcher.matchmate_backend.service;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
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

import java.util.List;
import java.util.UUID;

/**
 * Runs the actual grace-period purge for one user, called by {@link AccountPurgeScheduler} once
 * {@code scheduledPurgeAt} has lapsed (30 days after the user asked to delete their account).
 *
 * <p>This is a real, permanent deletion, matching the published Privacy Policy and Google Play's
 * account-deletion policy:
 * <ol>
 *   <li>the Firebase Authentication user is deleted, so the sign-in itself is gone;</li>
 *   <li>stored files (listing photos, avatar) are removed from storage;</li>
 *   <li>the {@code app_users} row is hard-deleted. Every table that references it
 *       ({@code listings}, comments, likes, reports, blocks, notification preferences,
 *       {@code conversations} and {@code messages}) is declared {@code ON DELETE CASCADE},
 *       so the user's chats and everything else go with it.</li>
 * </ol>
 * Signing in again with the same Google/phone account afterwards creates a brand-new Firebase
 * user and a fresh profile.
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
    private final FirebaseAuth firebaseAuth;

    public AccountPurgeService(
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            ListingCommentRepository listingCommentRepository,
            ListingLikeRepository listingLikeRepository,
            ListingCommentLikeRepository listingCommentLikeRepository,
            UserBlockRepository userBlockRepository,
            StorageService storageService,
            FirebaseAuth firebaseAuth
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.listingCommentRepository = listingCommentRepository;
        this.listingLikeRepository = listingLikeRepository;
        this.listingCommentLikeRepository = listingCommentLikeRepository;
        this.userBlockRepository = userBlockRepository;
        this.storageService = storageService;
        this.firebaseAuth = firebaseAuth;
    }

    /** Its own transaction per user, called from a different bean (the scheduler) so this method's @Transactional actually applies. */
    @Transactional
    public void purgeUser(UUID userId) {
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.PENDING_DELETION) {
            return; // already cancelled/changed by a race - no-op, not an error
        }

        // Suspension always wins: a suspended user's data is never purged by this job, even if
        // the grace period has lapsed. It stays parked until an admin lifts the suspension, so a
        // suspension can never be laundered by deleting and waiting it out.
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            log.info("Skipping purge of suspended account {} - suspension survives the grace period", userId);
            return;
        }

        // 1. Delete the Firebase login first. If this fails for any reason other than "already
        //    gone", the exception aborts the purge and the scheduler retries tomorrow, so the
        //    database never ends up deleted while the sign-in survives. Re-running after a
        //    later database failure is safe: a missing Firebase user counts as success.
        deleteFirebaseUser(user.getFirebaseUid(), userId);

        // 2. Stored files: listing photos, then the avatar.
        List<Listing> listings = listingRepository.findByHostUserId(userId);
        for (Listing listing : listings) {
            for (String url : listing.getPhotoUrls()) {
                if (storageService.isManagedUrl(url)) storageService.delete(url);
            }
        }
        if (user.getAvatarUrl() != null && storageService.isManagedUrl(user.getAvatarUrl())) {
            storageService.delete(user.getAvatarUrl());
        }

        // 3. Explicit deletes for content outside the user's own listings. (The cascade would
        //    also catch these; doing it here keeps the intent readable and independent of
        //    schema details.)
        listingRepository.deleteAll(listings);
        listingCommentRepository.deleteAllByUserId(userId);
        listingLikeRepository.deleteAllByUserId(userId);
        listingCommentLikeRepository.deleteAllByUserId(userId);
        userBlockRepository.deleteAllInvolvingUser(userId);

        // 4. Hard-delete the row. ON DELETE CASCADE removes the user's conversations and every
        //    chat message they sent or received, plus reports, notification preferences, etc.
        userRepository.delete(user);
        userRepository.flush();

        log.info("Permanently deleted account {} (Firebase user, chats and profile)", userId);
    }

    private void deleteFirebaseUser(String firebaseUid, UUID userId) {
        if (firebaseUid == null || firebaseUid.isBlank()) return;
        try {
            firebaseAuth.deleteUser(firebaseUid);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                log.info("Firebase user for account {} already deleted", userId);
                return;
            }
            throw new IllegalStateException(
                    "Could not delete Firebase user for account " + userId + ": " + e.getMessage(), e);
        }
    }
}
