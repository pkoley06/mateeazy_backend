package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.AdminUserCreateRequest;
import com.matcher.matchmate_backend.dto.request.AdminUserUpdateRequest;
import com.matcher.matchmate_backend.dto.response.AdminUserDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminUserSummaryResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.PrimaryAuthProvider;
import java.time.OffsetDateTime;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.storage.StorageService;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Service
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final NotificationPreferencesService preferencesService;
    private final StorageService storageService;

    public AdminUserService(
            AppUserRepository userRepository,
            NotificationPreferencesService preferencesService,
            StorageService storageService
    ) {
        this.userRepository = userRepository;
        this.preferencesService = preferencesService;
        this.storageService = storageService;
    }

    public PageResponse<AdminUserSummaryResponse> list(String search, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AppUser> result = StringUtils.hasText(search)
                ? userRepository.searchByNameEmailOrPhone(search.trim(), pageable)
                : userRepository.findAll(pageable);

        return PageResponse.of(result, this::toSummary);
    }

    public AdminUserDetailResponse getById(UUID id) {
        return toDetail(findUser(id));
    }

    @Transactional
    public AdminUserDetailResponse create(AdminUserCreateRequest request) {
        requireAdult(request.getDateOfBirth());

        AppUser user = AppUser.builder()
                .firebaseUid("admin-created:" + UUID.randomUUID())
                .primaryAuthProvider(PrimaryAuthProvider.ADMIN_CREATED)
                .fullName(request.getFullName())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .avatarUrl(request.getAvatarUrl())
                .occupationCategory(request.getOccupationCategory())
                .occupationTitle(request.getOccupationTitle())
                .bio(request.getBio())
                .smokingHabit(request.getSmokingHabit())
                .dietaryPreference(request.getDietaryPreference())
                .sleepSchedule(request.getSleepSchedule())
                .cleanlinessLevel(request.getCleanlinessLevel())
                .guestTolerance(request.getGuestTolerance())
                .onboardingCompleted(true)
                .build();

        if (StringUtils.hasText(request.getLastKnownLocality())) {
            user.setLastKnownLocality(request.getLastKnownLocality());
        }

        return toDetail(userRepository.save(user));
    }

    @Transactional
    public AdminUserDetailResponse update(UUID id, AdminUserUpdateRequest request) {
        AppUser user = findUser(id);

        if (request.getDateOfBirth() != null) {
            requireAdult(request.getDateOfBirth());
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (StringUtils.hasText(request.getFullName())) user.setFullName(request.getFullName());
        if (request.getGender() != null) user.setGender(request.getGender());
        // PATCH-style like the rest: absent = unchanged, blank = remove the photo.
        String previousAvatarUrl = user.getAvatarUrl();
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null);
        }
        if (request.getOccupationCategory() != null) user.setOccupationCategory(request.getOccupationCategory());
        if (request.getOccupationTitle() != null) user.setOccupationTitle(request.getOccupationTitle());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getLastKnownLocality() != null) user.setLastKnownLocality(request.getLastKnownLocality());
        if (request.getSmokingHabit() != null) user.setSmokingHabit(request.getSmokingHabit());
        if (request.getDietaryPreference() != null) user.setDietaryPreference(request.getDietaryPreference());
        if (request.getSleepSchedule() != null) user.setSleepSchedule(request.getSleepSchedule());
        if (request.getCleanlinessLevel() != null) user.setCleanlinessLevel(request.getCleanlinessLevel());
        if (request.getGuestTolerance() != null) user.setGuestTolerance(request.getGuestTolerance());

        AdminUserDetailResponse response = toDetail(userRepository.save(user));
        // Best-effort cleanup of the replaced photo; only ever touches objects in our own bucket.
        if (previousAvatarUrl != null
                && !previousAvatarUrl.equals(user.getAvatarUrl())
                && storageService.isManagedUrl(previousAvatarUrl)) {
            storageService.delete(previousAvatarUrl);
        }
        return response;
    }

    @Transactional
    public AdminUserDetailResponse setActive(UUID id, boolean active) {
        AppUser user = findUser(id);
        user.setIsActive(active);
        return toDetail(userRepository.save(user));
    }

    @Transactional
    public AdminUserDetailResponse setSuspended(UUID id, boolean suspended, String reason) {
        AppUser user = findUser(id);
        user.setIsSuspended(suspended);
        user.setSuspendedReason(suspended ? reason : null);
        return toDetail(userRepository.save(user));
    }

    private AppUser findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireAdult(LocalDate dateOfBirth) {
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < 18) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be at least 18 years old");
        }
    }

    private AdminUserSummaryResponse toSummary(AppUser user) {
        Integer age = user.getDateOfBirth() != null ? Period.between(user.getDateOfBirth(), LocalDate.now()).getYears() : null;
        return AdminUserSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .age(age)
                .lastKnownLocality(user.getLastKnownLocality())
                .primaryAuthProvider(user.getPrimaryAuthProvider())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .isActive(user.getIsActive())
                .isSuspended(user.getIsSuspended())
                .suspendedReason(user.getSuspendedReason())
                .accountStatus(user.getAccountStatus())
                .onboardingCompleted(user.getOnboardingCompleted())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AdminUserDetailResponse toDetail(AppUser user) {
        Integer age = user.getDateOfBirth() != null ? Period.between(user.getDateOfBirth(), LocalDate.now()).getYears() : null;
        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .primaryAuthProvider(user.getPrimaryAuthProvider())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
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
                .lastKnownLocality(user.getLastKnownLocality())
                .isActive(user.getIsActive())
                .isSuspended(user.getIsSuspended())
                .suspendedReason(user.getSuspendedReason())
                .accountStatus(user.getAccountStatus())
                .disabledAt(user.getDisabledAt())
                .isLookingForRoom(user.getIsLookingForRoom())
                .notificationPreferences(preferencesService.get(user.getId()))
                .onboardingCompleted(user.getOnboardingCompleted())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
