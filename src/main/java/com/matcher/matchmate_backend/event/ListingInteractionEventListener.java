package com.matcher.matchmate_backend.event;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.service.NotificationService;
import com.matcher.matchmate_backend.websocket.ListingCommentBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans out comment / like side effects once the owning transaction has committed:
 * STOMP broadcast to everyone viewing the room, a push to the host, and a push to everyone
 * else who has commented on the room (each checked against the recipient's notification
 * preferences inside {@link NotificationService}).
 */
@Component
public class ListingInteractionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ListingInteractionEventListener.class);

    private final ListingCommentBroadcaster broadcaster;
    private final NotificationService notificationService;
    private final AppUserRepository userRepository;

    public ListingInteractionEventListener(
            ListingCommentBroadcaster broadcaster,
            NotificationService notificationService,
            AppUserRepository userRepository
    ) {
        this.broadcaster = broadcaster;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentChanged(ListingCommentChangedEvent event) {
        try {
            broadcaster.broadcast(event.listingId(), event.payload());
        } catch (Exception e) {
            log.warn("[WS] Failed to broadcast comment event for listing #{}: {}", event.listingId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(ListingCommentCreatedEvent event) {
        if (!event.authoredByHost()) {
            userRepository.findById(event.hostUserId()).ifPresent((AppUser host) ->
                    notificationService.notifyHostOfNewComment(
                            event.listingId(), event.listingTitle(), event.listingLocality(),
                            host, event.authorUserId(), event.authorName(), event.content()));
        }
        // Everyone else in the thread (including after the host's own replies).
        notificationService.notifyParticipantsOfNewComment(
                event.listingId(), event.listingTitle(), event.listingLocality(),
                event.hostUserId(), event.authorUserId(), event.authorName(), event.content());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onListingLiked(ListingLikedEvent event) {
        if (event.likedByHost()) return;
        userRepository.findById(event.hostUserId()).ifPresent((AppUser host) ->
                notificationService.notifyHostOfListingLike(
                        event.listingId(), event.listingTitle(), event.listingLocality(),
                        host, event.likerUserId(), event.likerName()));
    }
}
