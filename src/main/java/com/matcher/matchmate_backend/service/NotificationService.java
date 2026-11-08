package com.matcher.matchmate_backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.UserNotificationPreferences;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification dispatch. Every send is gated by the recipient's {@link UserNotificationPreferences}:
 * the "nearby" fan-outs filter in SQL ({@code findMatchingSeekersForListing},
 * {@code findActiveListingsWithinRadius}); the one-to-one host alerts check in Java.
 * <p>
 * Comments, likes and matches are first <em>stored</em> in the user's inbox
 * ({@link UserNotificationService}) and then pushed if they have a device token; the push
 * carries the stored row's id as {@code notificationId} so the app can de-duplicate. Chat
 * messages are push-only (the app derives those entries from unread counts).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final MatchingHeuristicEngine matchingEngine;
    private final NotificationPreferencesService preferencesService;
    private final UserNotificationService inboxService;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            MatchingHeuristicEngine matchingEngine,
            NotificationPreferencesService preferencesService,
            UserNotificationService inboxService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.matchingEngine = matchingEngine;
        this.preferencesService = preferencesService;
        this.inboxService = inboxService;
        this.messagingTemplate = messagingTemplate;
    }

    /** Stores the notification, then pushes it (if the user has a token) with its id attached. */
    private void storeAndPush(AppUser recipient, String title, String body, Map<String, String> data) {
        UUID stored = inboxService.record(recipient.getId(), data.get("type"), title, body, data);
        if (stored != null) {
            data.put("notificationId", stored.toString());
        }
        try {
            Map<String, Object> wsPayload = new HashMap<>(data);
            wsPayload.put("title", title);
            wsPayload.put("body", body);
            messagingTemplate.convertAndSendToUser(recipient.getId().toString(), "/queue/notifications", wsPayload);
        } catch (Exception e) {
            log.debug("[WebSocket] Could not push notification to user #{}: {}", recipient.getId(), e.getMessage());
        }
        if (recipient.getFcmToken() == null || recipient.getFcmToken().isBlank()) return;
        sendPush(recipient.getFcmToken(), title, body, data);
    }

    /**
     * Tray tag of a conversation's card on Android: {@code chat_<conversationId>}. Chat pushes
     * are data-only there, so the app assigns this tag itself when it draws the card and its
     * native side matches on it to clear the conversation once read. Kept here so the two
     * sides share one definition.
     */
    public static String chatNotificationTag(UUID conversationId) {
        return "chat_" + conversationId;
    }

    /**
     * Sends an individual FCM push notification asynchronously.
     */
    @Async
    public void sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        sendPush(fcmToken, title, body, data, null, null);
    }

    /**
     * As {@link #sendPush(String, String, String, Map)}, additionally stamping the tray entry
     * with {@code tag} (Android notification tag) and {@code threadId} (APNs
     * {@code thread-id}) so the app can find and dismiss it later.
     */
    @Async
    public void sendPush(
            String fcmToken, String title, String body, Map<String, String> data, String tag, String threadId
    ) {
        if (fcmToken == null || fcmToken.isBlank()) return;

        if (FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM] (Mock) To: {} | Title: {} | Body: {} | Data: {}", fcmToken, title, body, data);
            return;
        }

        try {
            AndroidNotification.Builder androidNotification = AndroidNotification.builder()
                    .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                    .setChannelId("mateeazy_matches")
                    .setDefaultSound(true);
            if (tag != null && !tag.isBlank()) {
                androidNotification.setTag(tag);
            }
            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(androidNotification.build())
                    .build();

            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setAndroidConfig(androidConfig)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (threadId != null && !threadId.isBlank()) {
                builder.setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setThreadId(threadId)
                                .setSound("default")
                                .build())
                        .build());
            }

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            FirebaseMessaging.getInstance().sendAsync(builder.build());
            log.info("[FCM] Successfully queued push to token {} with title: '{}'", fcmToken, title);
        } catch (Exception e) {
            log.warn("[FCM] Failed to dispatch push notification: {}", e.getMessage());
        }
    }

    /**
     * Triggered when a new room is listed (either by a user or admin).
     * Finds all room seekers whose search radius covers the new listing's location and
     * who have "Rooms listed nearby" switched on, calculates the compatibility match
     * percentage, and notifies them.
     */
    @Async
    public void notifyMatchingSeekersForNewListing(Listing listing) {
        if (listing == null || listing.getLocation() == null || listing.getHostUser() == null) return;

        try {
            String genderPreference = listing.getGenderPreference() != null
                    ? listing.getGenderPreference().name()
                    : "ALL";
            List<AppUser> matchingSeekers = userRepository.findMatchingSeekersForListing(
                    listing.getLocation(),
                    listing.getHostUser().getId(),
                    genderPreference
            );

            log.info("[FCM] Found {} matching seekers within radius for listing #{}", matchingSeekers.size(), listing.getId());

            for (AppUser seeker : matchingSeekers) {
                MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(seeker, listing.getHostUser());
                if (match != null && match.isDisqualified()) {
                    log.info("[Match] Skipping seeker #{} for listing #{}: disqualified by lifestyle dealbreaker",
                            seeker.getId(), listing.getId());
                    continue;
                }
                int matchPercent = (match != null && match.matchPercentage() != null)
                        ? match.matchPercentage()
                        : 85;

                if (matchPercent < 50) {
                    log.info("[Match] Skipping seeker #{} for listing #{}: match percentage ({}%) below threshold",
                            seeker.getId(), listing.getId(), matchPercent);
                    continue;
                }

                String roomTypeStr = listing.getRoomType() != null
                        ? listing.getRoomType().name().replace('_', ' ')
                        : "place";

                String title = "🏠 New Room Match! (" + matchPercent + "% Match)";
                String body = listing.getHostUser().getFullName() + " listed a " + roomTypeStr
                        + " in " + listing.getLocalityName() + " within your search radius.";

                Map<String, String> data = new HashMap<>();
                data.put("type", "ROOM_LISTING_MATCH");
                data.put("listingId", String.valueOf(listing.getId()));
                data.put("matchPercentage", String.valueOf(matchPercent));
                data.put("hostUserId", String.valueOf(listing.getHostUser().getId()));
                data.put("locality", listing.getLocalityName() != null ? listing.getLocalityName() : "");
                data.put("route", "/discover/room/" + listing.getId());
                data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                data.put("title", title);
                data.put("body", body);

                storeAndPush(seeker, title, body, data);
            }
        } catch (Exception e) {
            log.error("[FCM] Error notifying seekers for listing #{}: {}", listing.getId(), e.getMessage());
        }
    }

    /**
     * Another user commented on the host's room. Called after the comment transaction
     * committed with scalar listing data, so nothing lazy is touched here. Skipped when
     * the host turned off "Likes & Comments".
     */
    @Async
    public void notifyHostOfNewComment(
            UUID listingId, String listingTitle, String listingLocality,
            AppUser host, UUID authorUserId, String authorName, String content
    ) {
        if (listingId == null || host == null) return;
        if (!preferencesService.allows(host.getId(), UserNotificationPreferences::getNotifyLikesComments)) {
            log.debug("[FCM] Host #{} muted likes & comments; skipping comment alert", host.getId());
            return;
        }

        try {
            String preview = content == null ? "" : content.trim();
            if (preview.length() > 90) preview = preview.substring(0, 87) + "…";

            String title = "💬 " + authorName + " asked about " + roomLabel(listingTitle, listingLocality);
            String body = preview.isEmpty() ? "Open the listing to reply." : "\"" + preview + "\"";

            Map<String, String> data = new HashMap<>();
            data.put("type", "LISTING_COMMENT");
            data.put("listingId", String.valueOf(listingId));
            data.put("commentAuthorId", String.valueOf(authorUserId));
            data.put("route", "/discover/room/" + listingId);
            data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

            storeAndPush(host, title, body, data);
        } catch (Exception e) {
            log.error("[FCM] Error notifying host #{} about a comment on listing #{}: {}",
                    host.getId(), listingId, e.getMessage());
        }
    }

    /** Someone saved the host's room. Same "Likes & Comments" switch as comment alerts. */
    @Async
    public void notifyHostOfListingLike(
            UUID listingId, String listingTitle, String listingLocality,
            AppUser host, UUID likerUserId, String likerName
    ) {
        if (listingId == null || host == null) return;
        if (!preferencesService.allows(host.getId(), UserNotificationPreferences::getNotifyLikesComments)) return;

        try {
            String title = "❤️ " + likerName + " liked " + roomLabel(listingTitle, listingLocality);
            String body = "Open their profile to see if you're a match.";

            Map<String, String> data = new HashMap<>();
            data.put("type", "LISTING_LIKE");
            data.put("listingId", String.valueOf(listingId));
            data.put("likerUserId", String.valueOf(likerUserId));
            data.put("route", "/users/" + likerUserId);
            data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

            storeAndPush(host, title, body, data);
        } catch (Exception e) {
            log.error("[FCM] Error notifying host #{} about a like on listing #{}: {}",
                    host.getId(), listingId, e.getMessage());
        }
    }

    /**
     * Triggered when a room finder updates their target location / search radius.
     * Finds active listings within that radius whose hosts have "Room seekers nearby"
     * switched on, calculates the compatibility match percentage, and notifies them.
     */
    @Async
    public void notifyHostsForNewSeeker(AppUser seeker) {
        if (seeker == null || seeker.getTargetSearchLocation() == null) return;

        try {
            double radiusKm = seeker.getSearchRadiusKm() != null ? seeker.getSearchRadiusKm() : 10.0;
            double radiusMeters = radiusKm * 1000.0;

            List<Listing> nearbyListings = listingRepository.findActiveListingsWithinRadius(
                    seeker.getTargetSearchLocation(),
                    radiusMeters,
                    seeker.getId(),
                    com.matcher.matchmate_backend.model.enums.listing.GenderPreference
                            .sqlFilterValue(seeker.effectiveRoomFinderGender())
            );

            log.info("[FCM] Found {} active listings for seeker #{} search radius ({} km)",
                    nearbyListings.size(), seeker.getId(), radiusKm);

            for (Listing listing : nearbyListings) {
                AppUser host = listing.getHostUser();
                if (host == null) continue;

                MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(seeker, host);
                if (match != null && match.isDisqualified()) {
                    log.info("[Match] Skipping host #{} for seeker #{}: disqualified by lifestyle dealbreaker",
                            host.getId(), seeker.getId());
                    continue;
                }
                int matchPercent = (match != null && match.matchPercentage() != null)
                        ? match.matchPercentage()
                        : 85;

                if (matchPercent < 50) {
                    log.info("[Match] Skipping host #{} for seeker #{}: match percentage ({}%) below threshold",
                            host.getId(), seeker.getId(), matchPercent);
                    continue;
                }

                String title = "👋 New Roommate Match! (" + matchPercent + "% Match)";
                String body = seeker.getFullName() + " is looking for a room in your area ("
                        + matchPercent + "% compatibility match).";

                Map<String, String> data = new HashMap<>();
                data.put("type", "ROOM_SEEKER_MATCH");
                data.put("seekerUserId", String.valueOf(seeker.getId()));
                data.put("matchPercentage", String.valueOf(matchPercent));
                data.put("listingId", String.valueOf(listing.getId()));
                data.put("route", "/users/" + seeker.getId());
                data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
                data.put("title", title);
                data.put("body", body);

                storeAndPush(host, title, body, data);
            }
        } catch (Exception e) {
            log.error("[FCM] Error notifying hosts for seeker #{}: {}", seeker.getId(), e.getMessage());
        }
    }

    /**
     * Someone sent the recipient a chat message. Called from {@code ChatEventListener} for
     * every message; the app itself drops the banner when that conversation is on screen.
     * Skipped when the recipient muted "Messages".
     * <p>
     * Nothing is persisted for this. On Android the push is <em>data-only</em>: the app's
     * background handler draws one conversation-style tray card per sender (every unread
     * message listed, with inline Reply / Mark as read) and clears it once the conversation
     * is read. iOS gets a normal alert with {@code thread-id} = conversation, which the
     * system groups by itself. The in-app "New message" entries come from the conversation's
     * unread count, not from a stored notification.
     */
    @Async
    public void notifyRecipientOfChatMessage(
            AppUser recipient, UUID conversationId, UUID senderId, String senderName, String senderAvatarUrl,
            UUID messageId, String messageText, OffsetDateTime sentAt
    ) {
        if (recipient == null) return;
        if (recipient.getFcmToken() == null || recipient.getFcmToken().isBlank()) return;
        if (!preferencesService.allows(recipient.getId(), UserNotificationPreferences::getNotifyMessages)) {
            log.debug("[FCM] User #{} muted messages; skipping chat push", recipient.getId());
            return;
        }

        try {
            String text = messageText == null ? "" : messageText.trim();
            String preview = text.length() > 90 ? text.substring(0, 87) + "…" : text;
            // The tray card shows the message itself, so it gets more than the alert line,
            // still bounded (FCM data payloads cap at 4 KB).
            String trayText = text.length() > 600 ? text.substring(0, 597) + "…" : text;

            String title = senderName != null && !senderName.isBlank() ? senderName : "New message";
            String body = preview.isEmpty() ? "Sent you a message." : preview;

            Map<String, String> data = new HashMap<>();
            data.put("type", "CHAT_MESSAGE");
            data.put("conversation_id", String.valueOf(conversationId));
            data.put("sender_id", String.valueOf(senderId));
            data.put("sender_name", senderName != null ? senderName : "");
            // The tray card shows the sender's photo; blank when they have none.
            data.put("sender_avatar_url", senderAvatarUrl != null ? senderAvatarUrl : "");
            data.put("message_id", String.valueOf(messageId));
            data.put("message_text", trayText);
            data.put("sent_at", sentAt != null ? sentAt.toString() : OffsetDateTime.now().toString());
            data.put("route", "/chat/" + conversationId);
            data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

            sendChatPush(recipient.getFcmToken(), title, body, data, String.valueOf(conversationId));
        } catch (Exception e) {
            log.error("[FCM] Error notifying user #{} about a chat message in conversation #{}: {}",
                    recipient.getId(), conversationId, e.getMessage());
        }
    }

    /**
     * Chat-specific dispatch: data-only with high priority on Android (so the app, not FCM,
     * draws the tray card - see the app's {@code ChatTrayNotifications}), and an alert with
     * {@code thread-id} on iOS.
     */
    @Async
    public void sendChatPush(String fcmToken, String title, String body, Map<String, String> data, String threadId) {
        if (fcmToken == null || fcmToken.isBlank()) return;

        if (FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM] (Mock chat) To: {} | Title: {} | Body: {} | Data: {}", fcmToken, title, body, data);
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder().setTitle(title).setBody(body).build())
                                    .setThreadId(threadId)
                                    .setCategory("CHAT_MESSAGE")
                                    .setSound("default")
                                    .build())
                            .build())
                    .build();

            FirebaseMessaging.getInstance().sendAsync(message);
            log.info("[FCM] Successfully queued chat push to token {} from '{}'", fcmToken, title);
        } catch (Exception e) {
            log.warn("[FCM] Failed to dispatch chat push: {}", e.getMessage());
        }
    }

    private static String roomLabel(String title, String locality) {
        if (title != null && !title.isBlank()) return title;
        return "your room in " + (locality != null ? locality : "your area");
    }
}
