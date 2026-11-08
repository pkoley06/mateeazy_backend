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
                : "/discover";

        List<AppUser> activeUsers = userRepository.findByIsActiveTrueAndIsSuspendedFalse();
        int recipientCount = activeUsers.size();

        Announcement saved = announcementRepository.save(Announcement.builder()
                .title(cleanTitle)
                .body(cleanBody)
                .imageUrl(cleanImage)
                .actionRoute(cleanRoute)
                .recipientsCount(recipientCount)
                .build());

        // Dispatch async broadcast to all users
        dispatchBroadcast(saved.getId(), cleanTitle, cleanBody, cleanImage, cleanRoute, activeUsers);

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
            List<AppUser> users
    ) {
        log.info("[Broadcast] Starting broadcast of festive event '{}' to {} users", title, users.size());

        for (AppUser user : users) {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("type", "FESTIVE_EVENT");
                data.put("title", title);
                data.put("body", body);
                if (imageUrl != null) {
                    data.put("imageUrl", imageUrl);
                    data.put("image", imageUrl);
                }
                data.put("route", route);
                data.put("announcementId", announcementId.toString());
                data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

                // 1. Store in user notification inbox
                UUID storedNotificationId = userNotificationService.record(
                        user.getId(),
                        "FESTIVE_EVENT",
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

        log.info("[Broadcast] Finished dispatching festive event '{}'", title);
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
