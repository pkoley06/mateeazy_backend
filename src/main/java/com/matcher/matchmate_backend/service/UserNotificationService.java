package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.dto.response.UserNotificationResponse;
import com.matcher.matchmate_backend.model.entity.UserNotification;
import com.matcher.matchmate_backend.repository.UserNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The stored notification inbox. {@link NotificationService} records a row for every
 * comment / like / match alert <em>before</em> trying the push, so a user without a device
 * token (or who installed the app later) still sees the event in the Notifications tab.
 */
@Service
public class UserNotificationService {

    private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);
    /** Rows kept per user; older ones are trimmed on insert. */
    private static final int MAX_PER_USER = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserNotificationRepository repository;
    private final ObjectMapper objectMapper;

    public UserNotificationService(UserNotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Stores a notification and returns its id (to be echoed in the push payload as
     * {@code notificationId}), or null when storing failed - the push still goes out.
     * Runs in its own transaction because callers are {@code @Async} listeners that fire
     * after the originating transaction committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(UUID userId, String type, String title, String body, Map<String, String> data) {
        try {
            UserNotification saved = repository.save(UserNotification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title == null ? "" : title)
                    .body(body == null ? "" : body)
                    .dataJson(toJson(data))
                    .build());
            repository.trimToNewest(userId, MAX_PER_USER);
            return saved.getId();
        } catch (Exception e) {
            log.warn("[Inbox] Could not store {} notification for user #{}: {}", type, userId, e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<UserNotificationResponse> list(UUID userId, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageResponse.of(
                repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(0, page), safeSize)),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public UserNotificationResponse markRead(UUID userId, UUID notificationId) {
        UserNotification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notification = repository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, OffsetDateTime.now());
    }

    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        repository.findByIdAndUserId(notificationId, userId).ifPresent(repository::delete);
    }

    @Transactional
    public int clearAll(UUID userId) {
        return repository.deleteAllForUser(userId);
    }

    private UserNotificationResponse toResponse(UserNotification n) {
        return UserNotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .data(fromJson(n.getDataJson()))
                .read(n.getReadAt() != null)
                .createdAt(n.getCreatedAt())
                .build();
    }

    private String toJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) return "{}";
        try {
            // TreeMap: deterministic key order, and drops nothing the push carried.
            return objectMapper.writeValueAsString(new TreeMap<>(data));
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> out = new LinkedHashMap<>();
            objectMapper.readTree(json).properties().forEach(entry ->
                    out.put(entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asText()));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
