package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.NotificationPreferencesRequest;
import com.matcher.matchmate_backend.dto.response.NotificationPreferencesResponse;
import com.matcher.matchmate_backend.model.entity.UserNotificationPreferences;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.UserNotificationPreferencesRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Granular push switches. A user without a row is treated as "all on", so the row is
 * only materialised on the first PATCH. Every FCM dispatch in {@link NotificationService}
 * goes through {@link #allows} (or the equivalent SQL join) before sending.
 */
@Service
public class NotificationPreferencesService {

    private final UserNotificationPreferencesRepository preferencesRepository;
    private final AppUserRepository userRepository;

    public NotificationPreferencesService(
            UserNotificationPreferencesRepository preferencesRepository,
            AppUserRepository userRepository
    ) {
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
    }

    public NotificationPreferencesResponse get(UUID userId) {
        return NotificationPreferencesResponse.from(current(userId));
    }

    @Transactional
    public NotificationPreferencesResponse update(UUID userId, NotificationPreferencesRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        UserNotificationPreferences prefs = current(userId);
        if (request.getNotifyLikesComments() != null) prefs.setNotifyLikesComments(request.getNotifyLikesComments());
        if (request.getNotifyMessages() != null) prefs.setNotifyMessages(request.getNotifyMessages());
        if (request.getNotifySeekersNearby() != null) prefs.setNotifySeekersNearby(request.getNotifySeekersNearby());
        if (request.getNotifyRoomsNearby() != null) prefs.setNotifyRoomsNearby(request.getNotifyRoomsNearby());
        return NotificationPreferencesResponse.from(preferencesRepository.save(prefs));
    }

    /** Legacy bridge for the search-preferences endpoint, which still exposes two of the switches. */
    @Transactional
    public void setNearbyAlerts(UUID userId, Boolean roomsNearby, Boolean seekersNearby) {
        if (roomsNearby == null && seekersNearby == null) return;
        UserNotificationPreferences prefs = current(userId);
        if (roomsNearby != null) prefs.setNotifyRoomsNearby(roomsNearby);
        if (seekersNearby != null) prefs.setNotifySeekersNearby(seekersNearby);
        preferencesRepository.save(prefs);
    }

    /** {@code true} when the given switch is on (or the user never customised anything). */
    public boolean allows(UUID userId, Predicate<UserNotificationPreferences> flag) {
        if (userId == null) return false;
        return preferencesRepository.findById(userId).map(flag::test).orElse(true);
    }

    /** Stored row or in-memory defaults (not persisted) when the user never customised anything. */
    public UserNotificationPreferences current(UUID userId) {
        return preferencesRepository.findById(userId)
                .orElseGet(() -> UserNotificationPreferences.defaultsFor(userId));
    }
}
