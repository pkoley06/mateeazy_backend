package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.UserSummaryResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.UserFollow;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFollowService {

    private final UserFollowRepository userFollowRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;
    private final NotificationPreferencesService notificationPreferencesService;

    @Transactional
    public String followUser(UUID followerId, UUID targetId) {
        if (followerId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot follow yourself");
        }

        AppUser follower = appUserRepository.findById(followerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follower not found"));
        AppUser target = appUserRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));

        Optional<UserFollow> existing = userFollowRepository.findByFollowerIdAndFollowingId(followerId, targetId);
        if (existing.isPresent()) {
            return existing.get().getStatus();
        }

        boolean direct = Boolean.TRUE.equals(target.getDirectFollowEnabled());
        String initialStatus = direct ? "ACCEPTED" : "PENDING";

        UserFollow follow = UserFollow.builder()
                .follower(follower)
                .following(target)
                .status(initialStatus)
                .build();
        UserFollow saved = userFollowRepository.save(follow);

        // Send Push / in-app notification if preferences allow
        if (notificationPreferencesService.allows(target.getId(), com.matcher.matchmate_backend.model.entity.UserNotificationPreferences::getNotifyFollowers)) {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("type", direct ? "NEW_FOLLOWER" : "FOLLOW_REQUEST");
                data.put("requesterId", follower.getId().toString());
                data.put("requesterName", follower.getFullName());
                if (follower.getAvatarUrl() != null) {
                    data.put("requesterAvatarUrl", follower.getAvatarUrl());
                    data.put("imageUrl", follower.getAvatarUrl());
                }
                data.put("followId", saved.getId().toString());

                String title = direct
                        ? follower.getFullName() + " started following you"
                        : follower.getFullName() + " requested to follow you";
                String body = direct
                        ? "Check out their profile and discover mutual rooms"
                        : "Tap to accept or decline the follow request";

                notificationService.sendPush(target.getFcmToken(), title, body, data);
            } catch (Exception e) {
                log.warn("Could not dispatch follow notification: {}", e.getMessage());
            }
        }

        return initialStatus;
    }

    @Transactional
    public void unfollowUser(UUID followerId, UUID targetId) {
        userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, targetId);
    }

    @Transactional
    public void acceptFollowRequest(UUID currentUserId, UUID followId) {
        UserFollow follow = userFollowRepository.findById(followId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follow request not found"));

        if (!follow.getFollowing().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to accept this request");
        }

        follow.setStatus("ACCEPTED");
        follow.setUpdatedAt(OffsetDateTime.now());
        userFollowRepository.save(follow);

        // Notify follower that request was accepted
        try {
            AppUser target = follow.getFollowing();
            AppUser follower = follow.getFollower();
            Map<String, String> data = new HashMap<>();
            data.put("type", "FOLLOW_ACCEPTED");
            data.put("targetId", target.getId().toString());
            data.put("targetName", target.getFullName());
            if (target.getAvatarUrl() != null) {
                data.put("imageUrl", target.getAvatarUrl());
            }

            notificationService.sendPush(
                    follower.getFcmToken(),
                    target.getFullName() + " accepted your follow request",
                    "You are now following " + target.getFullName(),
                    data
            );
        } catch (Exception e) {
            log.warn("Could not notify accepted follow request: {}", e.getMessage());
        }
    }

    @Transactional
    public void rejectFollowRequest(UUID currentUserId, UUID followId) {
        UserFollow follow = userFollowRepository.findById(followId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follow request not found"));

        if (!follow.getFollowing().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to reject this request");
        }

        userFollowRepository.delete(follow);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getFollowers(UUID targetUserId, UUID currentUserId, Pageable pageable) {
        return userFollowRepository.findByFollowingIdAndStatus(targetUserId, "ACCEPTED", pageable)
                .map(uf -> toUserSummary(uf.getFollower(), currentUserId));
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getFollowing(UUID targetUserId, UUID currentUserId, Pageable pageable) {
        return userFollowRepository.findByFollowerIdAndStatus(targetUserId, "ACCEPTED", pageable)
                .map(uf -> toUserSummary(uf.getFollowing(), currentUserId));
    }

    @Transactional(readOnly = true)
    public String getFollowStatus(UUID currentUserId, UUID targetUserId) {
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return "SELF";
        }
        return userFollowRepository.findByFollowerIdAndFollowingId(currentUserId, targetUserId)
                .map(UserFollow::getStatus)
                .orElse("NONE");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getFollowCounts(UUID userId) {
        long followers = userFollowRepository.countByFollowingIdAndStatus(userId, "ACCEPTED");
        long following = userFollowRepository.countByFollowerIdAndStatus(userId, "ACCEPTED");
        Map<String, Long> res = new HashMap<>();
        res.put("followersCount", followers);
        res.put("followingCount", following);
        return res;
    }

    public UserSummaryResponse toUserSummary(AppUser user, UUID currentUserId) {
        String followStatus = "NONE";
        boolean isSelf = currentUserId != null && currentUserId.equals(user.getId());
        if (currentUserId != null && !isSelf) {
            followStatus = userFollowRepository.findByFollowerIdAndFollowingId(currentUserId, user.getId())
                    .map(UserFollow::getStatus)
                    .orElse("NONE");
        }

        return UserSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .occupationTitle(user.getOccupationTitle())
                .locality(user.getLastKnownLocality())
                .isVerified(Boolean.TRUE.equals(user.getIsIdentityVerified()))
                .followStatus(followStatus)
                .isSelf(isSelf)
                .build();
    }
}
