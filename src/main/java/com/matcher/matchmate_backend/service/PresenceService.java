package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.PresenceResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.websocket.PresenceRegistry;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Online/last-seen bookkeeping. {@link PresenceRegistry} is the live, in-memory source of
 * truth for "online right now"; {@code app_users.is_online}/{@code last_seen_at} are a
 * durable mirror only - read after a restart, or for "last seen" once a user goes offline.
 *
 * <p>Depends on {@link ChatService} (for the on-reconnect delivery sweep); {@code ChatService}
 * never depends back on this class, so the two beans can't form a cycle.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final String TOPIC_PREFIX = "/topic/users/";
    private static final String TOPIC_SUFFIX = "/presence";

    private final PresenceRegistry presenceRegistry;
    private final AppUserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    public PresenceService(
            PresenceRegistry presenceRegistry,
            AppUserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            ChatService chatService
    ) {
        this.presenceRegistry = presenceRegistry;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
    }

    public static String topicFor(UUID userId) {
        return TOPIC_PREFIX + userId + TOPIC_SUFFIX;
    }

    @Transactional
    public void markOnline(UUID userId, String sessionId) {
        if (!presenceRegistry.registerSession(userId, sessionId)) {
            return; // not this user's first session - already online, nothing changed
        }
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        user.setIsOnline(true);
        userRepository.save(user);
        broadcast(userId, true, user.getLastSeenAt());

        try {
            chatService.sweepDeliveryOnConnect(userId);
        } catch (Exception e) {
            log.warn("[Presence] Failed to sweep pending deliveries for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void markOffline(UUID userId, String sessionId) {
        if (!presenceRegistry.unregisterSession(userId, sessionId)) {
            return; // not this user's last session - still online elsewhere
        }
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        OffsetDateTime now = OffsetDateTime.now();
        user.setIsOnline(false);
        user.setLastSeenAt(now);
        userRepository.save(user);
        broadcast(userId, false, now);
    }

    public PresenceResponse getPresence(UUID userId) {
        boolean online = presenceRegistry.isOnline(userId);
        OffsetDateTime lastSeenAt = userRepository.findById(userId)
                .map(AppUser::getLastSeenAt)
                .orElse(null);
        return PresenceResponse.builder()
                .isOnline(online)
                .lastSeenAt(lastSeenAt)
                .build();
    }

    private void broadcast(UUID userId, boolean isOnline, OffsetDateTime lastSeenAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", userId);
        payload.put("is_online", isOnline);
        payload.put("last_seen_at", lastSeenAt);
        // Cast needed: AbstractMessageSendingTemplate#convertAndSend is otherwise ambiguous
        // between the (destination, payload) and (destination, headers) overloads when the
        // payload's static type is itself a Map.
        messagingTemplate.convertAndSend(topicFor(userId), (Object) payload);
    }
}
