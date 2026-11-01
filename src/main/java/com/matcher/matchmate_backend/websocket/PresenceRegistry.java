package com.matcher.matchmate_backend.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session bookkeeping for presence: a user may have more than one open STOMP
 * session (multiple devices/tabs), so "online" means "at least one live session", and the
 * online/offline transition only fires when the first session opens or the last one closes.
 * Purely in-process - a restart (or a multi-instance deployment) relies on
 * {@code AppUserRepository.resetAllOnlineStatus()} / the persisted mirror on app_users, not
 * on this registry surviving.
 */
@Component
public class PresenceRegistry {

    private final Map<UUID, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public boolean isOnline(UUID userId) {
        if (userId == null) return false;
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * @return true only when this session is the user's first - an offline -> online transition.
     *
     * <p>Uses {@link Map#compute} rather than {@code computeIfAbsent} + an external
     * {@code synchronized} block: the latter has a real race where a concurrent
     * {@link #unregisterSession} can remove the map entry for an empty set in between this
     * method fetching that same set and adding to it, silently losing the new session (the
     * set it just mutated is no longer the one in the map). {@code compute} runs its whole
     * remapping function atomically per key, so the read-check-mutate-decide sequence can
     * never straddle a concurrent removal.
     */
    public boolean registerSession(UUID userId, String sessionId) {
        boolean[] wasEmpty = new boolean[1];
        sessionsByUser.compute(userId, (id, existing) -> {
            Set<String> sessions = existing != null ? existing : ConcurrentHashMap.newKeySet();
            wasEmpty[0] = sessions.isEmpty();
            sessions.add(sessionId);
            return sessions;
        });
        return wasEmpty[0];
    }

    /** @return true only when this was the user's last session - an online -> offline transition. */
    public boolean unregisterSession(UUID userId, String sessionId) {
        boolean[] becameEmpty = new boolean[1];
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(sessionId);
            becameEmpty[0] = sessions.isEmpty();
            return sessions.isEmpty() ? null : sessions; // returning null atomically removes the entry
        });
        return becameEmpty[0];
    }
}
