package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.AdminAnnouncementRequest;
import com.matcher.matchmate_backend.dto.response.AdminAnnouncementResponse;
import com.matcher.matchmate_backend.model.entity.Announcement;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.repository.AnnouncementRepository;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminAnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AdminAnnouncementService.class);

    private final AnnouncementRepository announcementRepository;
    private final AppUserRepository userRepository;
    private final UserNotificationService userNotificationService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public AdminAnnouncementService(
            AnnouncementRepository announcementRepository,
            AppUserRepository userRepository,
            UserNotificationService userNotificationService,
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
        this.userNotificationService = userNotificationService;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public AdminAnnouncementResponse broadcast(AdminAnnouncementRequest request) {
        String cleanTitle = request.getTitle().trim();
        String cleanBody = request.getBody().trim();
        String cleanImage = (request.getImageUrl() != null && !request.getImageUrl().isBlank())
                ? request.getImageUrl().trim()
                : null;
        String cleanRoute = (request.getActionRoute() != null && !request.getActionRoute().isBlank())
                ? request.getActionRoute().trim()
                : "/home";

        List<AppUser> targetUsers;
        boolean isExplicitBroadcastAll = Boolean.TRUE.equals(request.getBroadcastAll()) ||
                (request.getUserIds() == null && request.getBroadcastAll() == null);

        if (!isExplicitBroadcastAll && request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            targetUsers = userRepository.findAllById(request.getUserIds());
            log.info("[Broadcast] Targeted announcement to {} specific user(s): {}", targetUsers.size(), request.getUserIds());
        } else if (!isExplicitBroadcastAll) {
            targetUsers = List.of();
            log.warn("[Broadcast] Target audience set to specific users but userIds list was empty!");
        } else {
            targetUsers = userRepository.findByIsActiveTrueAndIsSuspendedFalse();
            log.info("[Broadcast] Broadcasting announcement to all {} active users", targetUsers.size());
        }
        int recipientCount = targetUsers.size();

        Announcement saved = announcementRepository.save(Announcement.builder()
                .title(cleanTitle)
                .body(cleanBody)
                .imageUrl(cleanImage)
                .actionRoute(cleanRoute)
                .recipientsCount(recipientCount)
                .build());

        // Dispatch async broadcast to target users
        dispatchBroadcast(saved.getId(), cleanTitle, cleanBody, cleanImage, cleanRoute, request.getType(), targetUsers);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminAnnouncementResponse> listAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Async
    public void dispatchBroadcast(
            UUID announcementId,
            String title,
            String body,
            String imageUrl,
            String route,
            String requestedType,
            List<AppUser> users
    ) {
        log.info("[Broadcast] Starting broadcast of announcement '{}' to {} users", title, users.size());

        // Determine notification type
        String type = (requestedType != null && !requestedType.isBlank()) ? requestedType.trim() : "FESTIVE_EVENT";
        String lowerRoute = route != null ? route.toLowerCase() : "";
        String lowerTitle = title != null ? title.toLowerCase() : "";
        String lowerBody = body != null ? body.toLowerCase() : "";
        if (lowerRoute.contains("play.google.com") || lowerRoute.startsWith("market://") || lowerRoute.equals("/update")
                || lowerTitle.contains("update") || lowerBody.contains("update")) {
            type = "APP_UPDATE";
        }

        for (AppUser user : users) {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("type", type);
                data.put("title", title);
                data.put("body", body);
                if (imageUrl != null) {
                    data.put("imageUrl", imageUrl);
                    data.put("image", imageUrl);
                }
                data.put("route", route);
                data.put("actionRoute", route);
                data.put("announcementId", announcementId.toString());
                data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

                // 1. Store in user notification inbox
                UUID storedNotificationId = userNotificationService.record(
                        user.getId(),
                        type,
                        title,
                        body,
                        data
                );
                if (storedNotificationId != null) {
                    data.put("notificationId", storedNotificationId.toString());
                }

                // 2. Real-time WebSocket delivery
                try {
                    Map<String, Object> wsPayload = new HashMap<>(data);
                    wsPayload.put("title", title);
                    wsPayload.put("body", body);
                    messagingTemplate.convertAndSendToUser(
                            user.getId().toString(),
                            "/queue/notifications",
                            wsPayload
                    );
                } catch (Exception wsEx) {
                    log.debug("[Broadcast] WebSocket delivery failed for user {}: {}", user.getId(), wsEx.getMessage());
                }

                // 3. FCM push notification (system tray with image)
                if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                    notificationService.sendPush(user.getFcmToken(), title, body, data);
                }
            } catch (Exception e) {
                log.warn("[Broadcast] Failed delivering announcement to user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("[Broadcast] Finished dispatching announcement '{}' (type={})", title, type);
    }

    private AdminAnnouncementResponse toResponse(Announcement a) {
        return AdminAnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .body(a.getBody())
                .imageUrl(a.getImageUrl())
                .actionRoute(a.getActionRoute())
                .recipientsCount(a.getRecipientsCount())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
