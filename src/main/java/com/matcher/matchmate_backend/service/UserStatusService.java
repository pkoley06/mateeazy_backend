package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.UserStatusCreateRequest;
import com.matcher.matchmate_backend.dto.response.UserStatusFeedGroup;
import com.matcher.matchmate_backend.dto.response.UserStatusResponse;
import com.matcher.matchmate_backend.model.entity.*;
import com.matcher.matchmate_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatusService {

    private final UserStatusRepository userStatusRepository;
    private final UserStatusViewRepository userStatusViewRepository;
    private final UserFollowRepository userFollowRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;

    @Transactional
    public UserStatusResponse createStatus(UUID userId, UserStatusCreateRequest request) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suspended users cannot post stories");
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserStatus status = UserStatus.builder()
                .user(user)
                .mediaUrl(request.getMediaUrl())
                .caption(request.getCaption() != null ? request.getCaption().trim() : null)
                .backgroundColor(request.getBackgroundColor())
                .createdAt(now)
                .expiresAt(now.plusHours(24))
                .build();

        UserStatus saved = userStatusRepository.save(status);
        return toStatusResponse(saved, userId);
    }

    @Transactional(readOnly = true)
    public List<UserStatusFeedGroup> getActiveFeed(UUID currentUserId) {
        OffsetDateTime now = OffsetDateTime.now();

        // Target users: people current user follows (ACCEPTED) + current user
        Set<UUID> targetUserIds = new HashSet<>();
        if (currentUserId != null) {
            targetUserIds.add(currentUserId);
            List<UserFollow> following = userFollowRepository.findByFollowerIdAndStatus(currentUserId, "ACCEPTED");
            for (UserFollow uf : following) {
                targetUserIds.add(uf.getFollowing().getId());
            }
        }

        List<UserStatus> statuses = targetUserIds.isEmpty()
                ? Collections.emptyList()
                : userStatusRepository.findByUserIdInAndExpiresAtAfterOrderByCreatedAtAsc(targetUserIds, now);

        // Group by user
        Map<UUID, List<UserStatus>> grouped = new LinkedHashMap<>();
        // Ensure current user comes first if they have status
        if (currentUserId != null) {
            grouped.put(currentUserId, new ArrayList<>());
        }

        for (UserStatus s : statuses) {
            grouped.computeIfAbsent(s.getUser().getId(), k -> new ArrayList<>()).add(s);
        }

        List<UserStatusFeedGroup> result = new ArrayList<>();
        for (Map.Entry<UUID, List<UserStatus>> entry : grouped.entrySet()) {
            List<UserStatus> userStatuses = entry.getValue();
            if (userStatuses.isEmpty()) continue;

            AppUser user = userStatuses.get(0).getUser();
            boolean isSelf = currentUserId != null && currentUserId.equals(user.getId());

            List<UserStatusResponse> items = userStatuses.stream()
                    .map(s -> toStatusResponse(s, currentUserId))
                    .collect(Collectors.toList());

            boolean hasUnviewed = items.stream().anyMatch(i -> !Boolean.TRUE.equals(i.getIsViewedByMe()));

            result.add(UserStatusFeedGroup.builder()
                    .userId(user.getId())
                    .userName(user.getFullName())
                    .userAvatarUrl(user.getAvatarUrl())
                    .isSelf(isSelf)
                    .hasUnviewed(hasUnviewed)
                    .statuses(items)
                    .build());
        }

        return result;
    }

    @Transactional
    public void deleteStatus(UUID statusId, UUID currentUserId) {
        UserStatus status = userStatusRepository.findById(statusId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status not found"));

        if (!status.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only delete your own status");
        }

        userStatusRepository.delete(status);
    }

    @Transactional
    public void recordView(UUID statusId, UUID viewerId) {
        UserStatus status = userStatusRepository.findById(statusId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status not found"));

        if (status.getUser().getId().equals(viewerId)) {
            return; // don't count self views
        }

        if (userStatusViewRepository.existsByStatusIdAndViewerId(statusId, viewerId)) {
            return;
        }

        AppUser viewer = appUserRepository.findById(viewerId).orElse(null);
        if (viewer == null) return;

        UserStatusView view = UserStatusView.builder()
                .status(status)
                .viewer(viewer)
                .build();
        userStatusViewRepository.save(view);
    }

    public UserStatusResponse toStatusResponse(UserStatus status, UUID currentUserId) {
        boolean isViewedByMe = false;
        boolean isSelf = currentUserId != null && currentUserId.equals(status.getUser().getId());
        if (currentUserId != null) {
            isViewedByMe = isSelf || userStatusViewRepository.existsByStatusIdAndViewerId(status.getId(), currentUserId);
        }

        long viewsCount = userStatusViewRepository.countByStatusId(status.getId());

        return UserStatusResponse.builder()
                .id(status.getId())
                .authorId(status.getUser().getId())
                .authorName(status.getUser().getFullName())
                .authorAvatarUrl(status.getUser().getAvatarUrl())
                .mediaUrl(status.getMediaUrl())
                .caption(status.getCaption())
                .backgroundColor(status.getBackgroundColor())
                .createdAt(status.getCreatedAt())
                .expiresAt(status.getExpiresAt())
                .viewsCount(viewsCount)
                .isViewedByMe(isViewedByMe)
                .isSelf(isSelf)
                .build();
    }
}
