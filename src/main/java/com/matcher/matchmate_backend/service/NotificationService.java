package com.matcher.matchmate_backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FCM dispatch. Every send is gated by the recipient's {@link UserNotificationPreferences}:
 * the "nearby" fan-outs filter in SQL ({@code findMatchingSeekersForListing},
 * {@code findActiveListingsWithinRadius}); the one-to-one host alerts check in Java.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final MatchingHeuristicEngine matchingEngine;
    private final NotificationPreferencesService preferencesService;

    public NotificationService(
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            MatchingHeuristicEngine matchingEngine,
            NotificationPreferencesService preferencesService
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.matchingEngine = matchingEngine;
        this.preferencesService = preferencesService;
    }

    /**
     * Sends an individual FCM push notification asynchronously.
     */
    @Async
    public void sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) return;

        if (FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM] (Mock) To: {} | Title: {} | Body: {} | Data: {}", fcmToken, title, body, data);
            return;
        }

        try {
            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                            .setChannelId("mateeazy_matches")
                            .setDefaultSound(true)
                            .build())
                    .build();

            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setAndroidConfig(androidConfig)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

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
            List<AppUser> matchingSeekers = userRepository.findMatchingSeekersForListing(
                    listing.getLocation(),
                    listing.getHostUser().getId()
            );

            log.info("[FCM] Found {} matching seekers within radius for listing #{}", matchingSeekers.size(), listing.getId());

            for (AppUser seeker : matchingSeekers) {
                if (seeker.getFcmToken() == null || seeker.getFcmToken().isBlank()) continue;

                MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(seeker, listing.getHostUser());
                int matchPercent = (match != null && match.matchPercentage() != null)
                        ? match.matchPercentage()
                        : 85;

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

                sendPush(seeker.getFcmToken(), title, body, data);
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
        if (host.getFcmToken() == null || host.getFcmToken().isBlank()) return;
        if (!preferencesService.allows(host.getId(), UserNotificationPreferences::getNotifyLikesComments)) {
            log.debug("[FCM] Host #{} muted likes & comments; skipping comment push", host.getId());
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

            sendPush(host.getFcmToken(), title, body, data);
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
        if (host.getFcmToken() == null || host.getFcmToken().isBlank()) return;
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

            sendPush(host.getFcmToken(), title, body, data);
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
                    seeker.getId()
            );

            log.info("[FCM] Found {} active listings for seeker #{} search radius ({} km)",
                    nearbyListings.size(), seeker.getId(), radiusKm);

            for (Listing listing : nearbyListings) {
                AppUser host = listing.getHostUser();
                if (host == null || host.getFcmToken() == null || host.getFcmToken().isBlank()) continue;

                MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(seeker, host);
                int matchPercent = (match != null && match.matchPercentage() != null)
                        ? match.matchPercentage()
                        : 85;

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

                sendPush(host.getFcmToken(), title, body, data);
            }
        } catch (Exception e) {
            log.error("[FCM] Error notifying hosts for seeker #{}: {}", seeker.getId(), e.getMessage());
        }
    }

    /**
     * Someone sent the recipient a chat message. Called from {@code ChatEventListener} only
     * when the recipient has no live presence session right now - if they're online they see
     * it over the socket instead. Skipped when they muted "Messages".
     */
    @Async
    public void notifyRecipientOfChatMessage(
            AppUser recipient, UUID conversationId, UUID senderId, String senderName, UUID messageId, String messageText
    ) {
        if (recipient == null) return;
        if (recipient.getFcmToken() == null || recipient.getFcmToken().isBlank()) return;
        if (!preferencesService.allows(recipient.getId(), UserNotificationPreferences::getNotifyMessages)) {
            log.debug("[FCM] User #{} muted messages; skipping chat push", recipient.getId());
            return;
        }

        try {
            String preview = messageText == null ? "" : messageText.trim();
            if (preview.length() > 90) preview = preview.substring(0, 87) + "…";

            String title = "💬 " + senderName;
            String body = preview.isEmpty() ? "Sent you a message." : preview;

            Map<String, String> data = new HashMap<>();
            data.put("type", "CHAT_MESSAGE");
            data.put("conversation_id", String.valueOf(conversationId));
            data.put("sender_id", String.valueOf(senderId));
            data.put("sender_name", senderName != null ? senderName : "");
            data.put("message_id", String.valueOf(messageId));
            data.put("message_text", preview);
            data.put("route", "/chat/" + conversationId);
            data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");

            sendPush(recipient.getFcmToken(), title, body, data);
        } catch (Exception e) {
            log.error("[FCM] Error notifying user #{} about a chat message in conversation #{}: {}",
                    recipient.getId(), conversationId, e.getMessage());
        }
    }

    private static String roomLabel(String title, String locality) {
        if (title != null && !title.isBlank()) return title;
        return "your room in " + (locality != null ? locality : "your area");
    }
}
