package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

/**
 * Bridges STOMP session lifecycle to {@link PresenceService}. A session is "connected" once
 * the broker has accepted the CONNECT frame (after {@code StompAuthChannelInterceptor} has
 * already authenticated it) and "disconnected" on socket close, client DISCONNECT, or a
 * heartbeat timeout - all of which fire {@code SessionDisconnectEvent}. Both event types
 * carry the authenticated {@link Principal} directly via {@code getUser()}
 * ({@code AbstractSubProtocolEvent}), so no separate sessionId -> userId lookup is needed.
 */
@Component
public class PresenceEventListener {

    private final PresenceService presenceService;

    public PresenceEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId == null) return;
        String sessionId = SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId();
        presenceService.markOnline(userId, sessionId);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId == null) return;
        presenceService.markOffline(userId, event.getSessionId());
    }

    private static UUID extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUserPrincipal user) {
            return user.getUserId();
        }
        return null;
    }
}
