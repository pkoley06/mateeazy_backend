package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.SearchPreferencesRequest;
import com.matcher.matchmate_backend.dto.response.SearchPreferencesResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.UserNotificationPreferences;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Alert area & radius (target location + km) and Room Finder Mode. The two legacy
 * {@code notify*} fields are still served and accepted for older clients but are stored in
 * {@link UserNotificationPreferences} since V12.
 */
@Service
public class SearchPreferencesService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final AppUserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationPreferencesService preferencesService;

    public SearchPreferencesService(
            AppUserRepository userRepository,
            NotificationService notificationService,
            NotificationPreferencesService preferencesService
    ) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.preferencesService = preferencesService;
    }

    @Transactional
    public void updateFcmToken(UUID userId, String token) {
        // The same physical device's token must never be live on two accounts at once - clear
        // it off whoever else holds it (typically a previous user who signed out on this device
        // without the client getting a chance to explicitly unregister it) before claiming it.
        userRepository.clearFcmTokenForOtherUsers(token, userId);
        AppUser user = getUser(userId);
        user.setFcmToken(token);
        userRepository.save(user);
    }

    /** Called on sign-out so a push meant for this user never lands on the device after someone else signs in on it. */
    @Transactional
    public void clearFcmToken(UUID userId) {
        AppUser user = getUser(userId);
        user.setFcmToken(null);
        userRepository.save(user);
    }

    public SearchPreferencesResponse getPreferences(UUID userId) {
        AppUser user = getUser(userId);
        Point loc = user.getTargetSearchLocation() != null ? user.getTargetSearchLocation() : user.getLastKnownLocation();
        UserNotificationPreferences prefs = preferencesService.current(userId);

        return SearchPreferencesResponse.builder()
                .latitude(loc != null ? loc.getY() : 22.5726)
                .longitude(loc != null ? loc.getX() : 88.3639)
                .locality(user.getTargetSearchLocality() != null ? user.getTargetSearchLocality() : user.getLastKnownLocality())
                .searchRadiusKm(user.getSearchRadiusKm() != null ? user.getSearchRadiusKm() : 10.0)
                .isLookingForRoom(user.getIsLookingForRoom() != null ? user.getIsLookingForRoom() : true)
                .notifyRoomFinder(Boolean.TRUE.equals(prefs.getNotifyRoomsNearby()))
                .notifyRoommateFinder(Boolean.TRUE.equals(prefs.getNotifySeekersNearby()))
                .build();
    }

    @Transactional
    public SearchPreferencesResponse updatePreferences(UUID userId, SearchPreferencesRequest request) {
        AppUser user = getUser(userId);

        Point targetPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
        user.setTargetSearchLocation(targetPoint);
        if (request.getLocality() != null && !request.getLocality().isBlank()) {
            user.setTargetSearchLocality(request.getLocality().trim());
        }
        if (request.getSearchRadiusKm() != null) {
            user.setSearchRadiusKm(request.getSearchRadiusKm());
        }
        if (request.getIsLookingForRoom() != null) {
            user.setIsLookingForRoom(request.getIsLookingForRoom());
        }
        preferencesService.setNearbyAlerts(userId, request.getNotifyRoomFinder(), request.getNotifyRoommateFinder());

        AppUser saved = userRepository.save(user);

        // If the user is actively searching, notify nearby active listing hosts about the new matching seeker!
        if (Boolean.TRUE.equals(saved.getIsLookingForRoom())) {
            notificationService.notifyHostsForNewSeeker(saved);
        }

        return getPreferences(saved.getId());
    }

    private AppUser getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
