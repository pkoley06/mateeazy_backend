package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Authenticates the STOMP session on CONNECT from the {@code Authorization: Bearer <jwt>}
 * native header (same user JWT the REST API uses) and guards every later SUBSCRIBE / SEND:
 * no principal → rejected; SUBSCRIBE only to a room comment topic or the caller's own error
 * queue; SEND only to application destinations. Throwing here makes the broker reply with
 * a STOMP ERROR frame and close the session.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final Pattern ROOM_COMMENTS_TOPIC = Pattern.compile("^/topic/rooms/[0-9a-fA-F-]{36}/comments$");
    private static final String USER_ERROR_QUEUE = "/user/queue/errors";
    private static final Pattern PRESENCE_TOPIC = Pattern.compile("^/topic/users/[0-9a-fA-F-]{36}/presence$");
    private static final String USER_MESSAGES_QUEUE = "/user/queue/messages";
    private static final String USER_MESSAGE_STATUS_QUEUE = "/user/queue/messages/status";

    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserRepository userRepository;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider, AppUserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> {
                requireUser(accessor, StompCommand.SUBSCRIBE);
                String destination = accessor.getDestination();
                boolean allowed = destination != null
                        && (ROOM_COMMENTS_TOPIC.matcher(destination).matches()
                            || USER_ERROR_QUEUE.equals(destination)
                            || PRESENCE_TOPIC.matcher(destination).matches()
                            || USER_MESSAGES_QUEUE.equals(destination)
                            || USER_MESSAGE_STATUS_QUEUE.equals(destination));
                if (!allowed) {
                    throw new AccessDeniedException("Subscription to " + destination + " is not allowed");
                }
            }
            case SEND -> {
                requireUser(accessor, StompCommand.SEND);
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith("/app/")) {
                    throw new AccessDeniedException("Clients may only send to application destinations");
                }
            }
            default -> { /* DISCONNECT, ACK, HEARTBEAT… need no checks */ }
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token) || jwtTokenProvider.isAdminToken(token)) {
            throw new BadCredentialsException("Missing or invalid access token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new AccessDeniedException("ACCOUNT_SUSPENDED");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AccessDeniedException("ACCOUNT_DISABLED");
        }

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(userId, jwtTokenProvider.getFirebaseUidFromToken(token));
        log.debug("[WS] STOMP session {} authenticated as user #{}", accessor.getSessionId(), userId);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static void requireUser(StompHeaderAccessor accessor, StompCommand command) {
        if (accessor.getUser() == null) {
            throw new AccessDeniedException(command + " requires an authenticated session");
        }
    }

    /** {@code Authorization: Bearer x} is canonical; a bare {@code token} header is accepted for SockJS clients. */
    private static String extractToken(StompHeaderAccessor accessor) {
        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return accessor.getFirstNativeHeader("token");
    }
}
