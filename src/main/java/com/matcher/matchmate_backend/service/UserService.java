package com.matcher.matchmate_backend.service;


import com.matcher.matchmate_backend.dto.request.DemographicsRequest;
import com.matcher.matchmate_backend.dto.request.DeviceLocationRequest;
import com.matcher.matchmate_backend.dto.request.LifestyleRequest;
import com.matcher.matchmate_backend.dto.response.ListingResponse;
import com.matcher.matchmate_backend.dto.response.OtherUserProfileResponse;
import com.matcher.matchmate_backend.dto.response.UserDemographicsResponse;
import com.matcher.matchmate_backend.dto.response.UserProfileResponse;
import com.matcher.matchmate_backend.model.entity.AppBranding;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.repository.AppBrandingRepository;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.storage.StorageService;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    /**
     * Bare-essentials onboarding alone is worth 40%; each of the five lifestyle
     * questions is worth an equal share of the remaining 60%, so a fully answered
     * profile reaches 100%.
     */
    private static final int BASE_PROFILE_STRENGTH = 40;
    private static final int POINTS_PER_LIFESTYLE_ANSWER = 12;

    /** Width of {@code app_users.last_known_locality}. */
    private static final int MAX_LOCALITY_LENGTH = 100;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final ListingService listingService;
    private final NotificationService notificationService;
    private final StorageService storageService;
    private final AppBrandingRepository appBrandingRepository;

    public UserService(
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            ListingService listingService,
            NotificationService notificationService,
            StorageService storageService,
            AppBrandingRepository appBrandingRepository
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.listingService = listingService;
        this.notificationService = notificationService;
        this.storageService = storageService;
        this.appBrandingRepository = appBrandingRepository;
    }

    /**
     * PATCH /api/v1/users/me/room-seeker — the Discover header toggle. Turning it on
     * re-runs the "new seeker" host alerts when the user already has a search area set.
     */
    @Transactional
    public UserProfileResponse updateRoomSeeker(
            UUID userId, Boolean isRoomSeeker, com.matcher.matchmate_backend.model.enums.listing.GenderPreference roomFinderGender
    ) {
        if (isRoomSeeker == null && roomFinderGender == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "is_room_seeker or room_finder_gender is required");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean wasSeeker = Boolean.TRUE.equals(user.getIsLookingForRoom());
        if (isRoomSeeker != null) {
            user.setIsLookingForRoom(isRoomSeeker);
        }
        if (roomFinderGender != null) {
            user.setRoomFinderGender(roomFinderGender);
        }
        AppUser saved = userRepository.save(user);
        boolean turnedOn = Boolean.TRUE.equals(isRoomSeeker) && !wasSeeker;
        if (turnedOn && saved.getTargetSearchLocation() != null) {
            notificationService.notifyHostsForNewSeeker(saved);
        }
        return toProfileResponse(saved);
    }

    @Transactional
    public UserDemographicsResponse updateDemographics(UUID userId, DemographicsRequest request){
        // Enforce Core Product Principle: Age >= 18 validation
        int age = Period.between((request.getDateOfBirth()), LocalDate.now()).getYears();

        if(age < 18){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be at least 18 years old");
        }

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String previousAvatarUrl = user.getAvatarUrl();

        user.setFullName(request.getFullName());
        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());
        if(request.getAvatarUrl() != null){
            user.setAvatarUrl(request.getAvatarUrl());
        }
        user.setOccupationCategory(request.getOccupationCategory());
        user.setOccupationTitle(request.getOccupationTitle());
        user.setBio(request.getBio());
        user.setOnboardingCompleted(true);

        AppUser updated = userRepository.save(user);

        // Best-effort cleanup of the replaced photo. Never touches URLs we didn't hand out
        // ourselves (e.g. the Unsplash preset avatars), and never fails the request.
        if (previousAvatarUrl != null
                && !previousAvatarUrl.equals(updated.getAvatarUrl())
                && storageService.isManagedUrl(previousAvatarUrl)) {
            storageService.delete(previousAvatarUrl);
        }

        return UserDemographicsResponse.builder()
                .userId(updated.getId())
                .fullName(updated.getFullName())
                .gender(updated.getGender())
                .dateOfBirth(updated.getDateOfBirth())
                .age(age)
                .avatarUrl(updated.getAvatarUrl())
                .occupationCategory(updated.getOccupationCategory())
                .occupationTitle(updated.getOccupationTitle())
                .bio(updated.getBio())
                .profileStrength(computeProfileStrength(updated))
                .isProfileComplete(true)
                .build();

    }

    /**
     * Progressive lifestyle vector update (Milestone 3 Step 3.2): applies only the
     * non-null fields sent, so each lifestyle card can be answered independently
     * without resetting the others.
     */
    @Transactional
    public UserProfileResponse updateLifestyle(UUID userId, LifestyleRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.getSmokingHabit() != null) user.setSmokingHabit(request.getSmokingHabit());
        if (request.getDietaryPreference() != null) user.setDietaryPreference(request.getDietaryPreference());
        if (request.getSleepSchedule() != null) user.setSleepSchedule(request.getSleepSchedule());
        if (request.getCleanlinessLevel() != null) user.setCleanlinessLevel(request.getCleanlinessLevel());
        if (request.getGuestTolerance() != null) user.setGuestTolerance(request.getGuestTolerance());

        AppUser updated = userRepository.save(user);
        return toProfileResponse(updated);
    }

    /**
     * PUT /api/v1/users/me/location — the device's own position (never a searched place).
     * Replaces the "Kolkata" default on {@code last_known_locality}, which is what other people
     * see as this user's area and where a Room Finder Mode profile is placed until they pick a
     * search area of their own.
     */
    @Transactional
    public void updateDeviceLocation(UUID userId, DeviceLocationRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setLastKnownLocation(GEOMETRY_FACTORY.createPoint(
                new Coordinate(request.getLongitude(), request.getLatitude())));
        String locality = request.getLocality() != null ? request.getLocality().trim() : "";
        if (!locality.isEmpty()) {
            user.setLastKnownLocality(locality.length() > MAX_LOCALITY_LENGTH
                    ? locality.substring(0, MAX_LOCALITY_LENGTH)
                    : locality);
        }
        userRepository.save(user);
    }

    /** GET /api/v1/users/me — full profile, used by the Profile strength screen. */
    public UserProfileResponse getMyProfile(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toProfileResponse(user);
    }

    /** GET /api/v1/users/{id} — other user's public profile and their active listings. */
    @Transactional // toResponse reads the lazy host; open-in-view is off
    public OtherUserProfileResponse getOtherUserProfile(UUID targetUserId) {
        AppUser user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile is unavailable");
        }

        int age = Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();

        List<Listing> listings = listingRepository.findByHostUserId(targetUserId)
                .stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsAvailable()))
                .toList();

        List<ListingResponse> listingResponses = listings.stream()
                .map(listingService::toResponse)
                .toList();

        return OtherUserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .gender(user.getGender())
                .age(age)
                .avatarUrl(user.getAvatarUrl())
                .occupationCategory(user.getOccupationCategory())
                .occupationTitle(user.getOccupationTitle())
                .bio(user.getBio())
                .smokingHabit(user.getSmokingHabit())
                .dietaryPreference(user.getDietaryPreference())
                .sleepSchedule(user.getSleepSchedule())
                .cleanlinessLevel(user.getCleanlinessLevel())
                .guestTolerance(user.getGuestTolerance())
                .locality(user.getLastKnownLocality())
                .isRoomSeeker(Boolean.TRUE.equals(user.getIsLookingForRoom()))
                .activeListingsCount(listingResponses.size())
                .activeListings(listingResponses)
                .build();
    }

    /**
     * POST /api/v1/users/me/disable — self-service, reversible pause.
     * <p>
     * Marks the account {@link AccountStatus#DISABLED}, stamps {@code disabled_at} and clears
     * {@code is_active}. Because {@link com.matcher.matchmate_backend.security.JwtAuthenticationFilter}
     * rejects every request from an inactive user, all previously issued JWTs are revoked at once
     * (tokens are stateless, so the active flag *is* the revocation list). Discovery, roommate
     * search and listing-visibility queries all filter on {@code is_active = true}, so the user and
     * their rooms disappear from other people's feeds immediately. The FCM token is dropped so no
     * pushes reach the device while disabled. Signing in again re-activates the account
     * (see AuthService).
     */
    @Transactional
    public void disableMyAccount(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getAccountStatus() == AccountStatus.DELETED
                || user.getAccountStatus() == AccountStatus.PENDING_DELETION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has already been deleted");
        }
        user.setIsActive(false);
        user.setAccountStatus(AccountStatus.DISABLED);
        user.setDisabledAt(OffsetDateTime.now());
        user.setFcmToken(null);
        userRepository.save(user);
    }

    /**
     * DELETE /api/v1/users/me — starts the grace-period deletion flow. Delists listings
     * immediately (so the user disappears from discovery right away) but defers scrubbing
     * profile fields and destroying content until {@code AccountPurgeScheduler} runs after the
     * grace period, so cancelling within the window restores a fully intact account.
     */
    @Transactional
    public void deleteMyAccount(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getAccountStatus() == AccountStatus.PENDING_DELETION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account deletion is already pending");
        }

        int graceDays = appBrandingRepository.findById(1L)
                .map(AppBranding::getDeletionGracePeriodDays)
                .orElse(30);

        OffsetDateTime now = OffsetDateTime.now();
        user.setIsActive(false);
        user.setAccountStatus(AccountStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(now);
        user.setScheduledPurgeAt(now.plusDays(graceDays));
        user.setFcmToken(null);
        userRepository.save(user);

        List<Listing> userListings = listingRepository.findByHostUserId(userId);
        for (Listing listing : userListings) {
            listing.setIsAvailable(false);
        }
        listingRepository.saveAll(userListings);
    }

    /**
     * POST /api/v1/users/me/cancel-deletion — the one call {@code JwtAuthenticationFilter} lets
     * a PENDING_DELETION user make. Valid only while still PENDING_DELETION and the grace period
     * hasn't lapsed yet.
     */
    @Transactional
    public void cancelDeletion(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getAccountStatus() != AccountStatus.PENDING_DELETION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No pending deletion to cancel");
        }
        if (user.getScheduledPurgeAt() != null && user.getScheduledPurgeAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "The grace period has already elapsed");
        }
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setIsActive(true);
        user.setDeletionRequestedAt(null);
        user.setScheduledPurgeAt(null);
        userRepository.save(user);
        // Listings stay delisted (isAvailable=false) - re-listing is a deliberate separate user
        // action via the normal listing-edit flow, not implicit on cancel.
    }

    private UserProfileResponse toProfileResponse(AppUser user) {
        int age = Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();

        return UserProfileResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .age(age)
                .avatarUrl(user.getAvatarUrl())
                .occupationCategory(user.getOccupationCategory())
                .occupationTitle(user.getOccupationTitle())
                .bio(user.getBio())
                .smokingHabit(user.getSmokingHabit())
                .dietaryPreference(user.getDietaryPreference())
                .sleepSchedule(user.getSleepSchedule())
                .cleanlinessLevel(user.getCleanlinessLevel())
                .guestTolerance(user.getGuestTolerance())
                .profileStrength(computeProfileStrength(user))
                .isProfileComplete(user.getOnboardingCompleted())
                .missingLifestyleFields(missingLifestyleFields(user))
                .isRoomSeeker(user.getIsLookingForRoom() != null ? user.getIsLookingForRoom() : Boolean.FALSE)
                .roomFinderGender(user.effectiveRoomFinderGender())
                .build();
    }

    private int computeProfileStrength(AppUser user) {
        if (!Boolean.TRUE.equals(user.getOnboardingCompleted())) return 0;
        int answered = 5 - missingLifestyleFields(user).size();
        return Math.min(100, BASE_PROFILE_STRENGTH + answered * POINTS_PER_LIFESTYLE_ANSWER);
    }

    private List<String> missingLifestyleFields(AppUser user) {
        List<String> missing = new ArrayList<>();
        if (user.getSmokingHabit() == null) missing.add("smokingHabit");
        if (user.getDietaryPreference() == null) missing.add("dietaryPreference");
        if (user.getSleepSchedule() == null) missing.add("sleepSchedule");
        if (user.getCleanlinessLevel() == null) missing.add("cleanlinessLevel");
        if (user.getGuestTolerance() == null) missing.add("guestTolerance");
        return missing;
    }
}
