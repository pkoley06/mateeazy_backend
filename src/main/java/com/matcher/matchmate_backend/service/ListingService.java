package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.ListingCreateRequest;
import com.matcher.matchmate_backend.dto.request.ListingUpdateRequest;
import com.matcher.matchmate_backend.dto.response.ListingResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.storage.StorageService;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Listings owned by app users: create / read / list-mine plus owner-only update and delete.
 */
@Service
public class ListingService {

    /** SRID 4326 = WGS 84, same standard used by every column/query in this codebase. */
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ListingRepository listingRepository;
    private final AppUserRepository userRepository;
    private final NotificationService notificationService;
    private final StorageService storageService;

    public ListingService(
            ListingRepository listingRepository,
            AppUserRepository userRepository,
            NotificationService notificationService,
            StorageService storageService
    ) {
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.storageService = storageService;
    }

    @Transactional
    public ListingResponse create(UUID hostUserId, ListingCreateRequest request) {
        AppUser host = userRepository.findById(hostUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Listing listing = Listing.builder()
                .hostUser(host)
                .listingType(request.getListingType())
                .title(request.getTitle())
                .rentAmount(request.getRentAmount())
                .depositAmount(request.getDepositAmount())
                .roomType(request.getRoomType())
                .furnishingStatus(request.getFurnishingStatus())
                .location(toPoint(request.getLatitude(), request.getLongitude()))
                .localityName(request.getLocalityName())
                .city(request.getCity())
                .stateName(trimToNull(request.getStateName()))
                .postalCode(trimToNull(request.getPostalCode()))
                .description(request.getDescription())
                .photoUrls(request.getPhotoUrls() != null ? new ArrayList<>(request.getPhotoUrls()) : new ArrayList<>())
                .moveInDate(request.getMoveInDate())
                .amenities(request.getAmenities() != null ? new ArrayList<>(request.getAmenities()) : new ArrayList<>())
                .genderPreference(resolveGenderPreference(request.getGenderPreference(), request.getOpenToAllGenders(), host))
                .kitchenType(request.getKitchenType())
                .washroomType(request.getWashroomType())
                .isAvailable(true)
                .build();

        Listing saved = listingRepository.save(listing);
        notificationService.notifyMatchingSeekersForNewListing(saved);
        return toResponse(saved);
    }

    /**
     * Owner-only partial update. Fields left {@code null} in the request keep their stored
     * value, so the client may send the whole pre-filled form or only the delta.
     */
    @Transactional
    public ListingResponse update(UUID requesterUserId, UUID listingId, ListingUpdateRequest request) {
        Listing listing = findOwnedListing(requesterUserId, listingId);

        if (request.getListingType() != null) listing.setListingType(request.getListingType());
        if (request.getTitle() != null && !request.getTitle().isBlank()) listing.setTitle(request.getTitle().trim());
        if (request.getRentAmount() != null) listing.setRentAmount(request.getRentAmount());
        if (request.getDepositAmount() != null) listing.setDepositAmount(request.getDepositAmount());
        if (request.getRoomType() != null) listing.setRoomType(request.getRoomType());
        if (request.getFurnishingStatus() != null) listing.setFurnishingStatus(request.getFurnishingStatus());
        if (request.getLatitude() != null && request.getLongitude() != null) {
            listing.setLocation(toPoint(request.getLatitude(), request.getLongitude()));
        }
        if (request.getLocalityName() != null && !request.getLocalityName().isBlank()) {
            listing.setLocalityName(request.getLocalityName().trim());
        }
        if (request.getCity() != null && !request.getCity().isBlank()) listing.setCity(request.getCity().trim());
        if (request.getStateName() != null) listing.setStateName(trimToNull(request.getStateName()));
        if (request.getPostalCode() != null) listing.setPostalCode(trimToNull(request.getPostalCode()));
        if (request.getDescription() != null) listing.setDescription(request.getDescription());
        List<String> removedPhotoUrls = List.of();
        if (request.getPhotoUrls() != null) {
            List<String> previousPhotoUrls = listing.getPhotoUrls();
            removedPhotoUrls = previousPhotoUrls.stream()
                    .filter(url -> !request.getPhotoUrls().contains(url))
                    .toList();
            listing.setPhotoUrls(new ArrayList<>(request.getPhotoUrls()));
        }
        if (request.getMoveInDate() != null) listing.setMoveInDate(request.getMoveInDate());
        if (request.getAmenities() != null) listing.setAmenities(new ArrayList<>(request.getAmenities()));
        if (request.getGenderPreference() != null) {
            listing.setGenderPreference(request.getGenderPreference());
        } else if (request.getOpenToAllGenders() != null) {
            listing.setGenderPreference(GenderPreference.fromLegacy(request.getOpenToAllGenders(), listing.getHostUser().getGender()));
        }
        if (request.getIsAvailable() != null) listing.setIsAvailable(request.getIsAvailable());
        if (request.getKitchenType() != null) listing.setKitchenType(request.getKitchenType());
        if (request.getWashroomType() != null) listing.setWashroomType(request.getWashroomType());

        ListingResponse response = toResponse(listingRepository.save(listing));
        // Best-effort cleanup of photos the owner just removed. Never fails the request.
        removedPhotoUrls.stream().filter(storageService::isManagedUrl).forEach(storageService::delete);
        return response;
    }

    /** Owner-only hard delete. Likes, comments and reports cascade at the database level (V8). */
    @Transactional
    public void delete(UUID requesterUserId, UUID listingId) {
        Listing listing = findOwnedListing(requesterUserId, listingId);
        List<String> photoUrls = listing.getPhotoUrls();
        listingRepository.delete(listing);
        photoUrls.stream().filter(storageService::isManagedUrl).forEach(storageService::delete);
    }

    @Transactional
    public ListingResponse getById(UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        AppUser host = listing.getHostUser();
        if (host != null && (!Boolean.TRUE.equals(host.getIsActive()) || Boolean.TRUE.equals(host.getIsSuspended()))) {
            // Disabled, deleted or suspended hosts are excluded from discovery; keep direct links consistent.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        return toResponse(listing);
    }

    @Transactional
    public List<ListingResponse> getMine(UUID hostUserId) {
        return listingRepository.findByHostUserId(hostUserId).stream().map(this::toResponse).toList();
    }

    private Listing findOwnedListing(UUID requesterUserId, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (listing.getHostUser() == null || !listing.getHostUser().getId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own listings");
        }
        return listing;
    }

    private Point toPoint(double latitude, double longitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    /** Explicit preference wins; otherwise bridge the legacy boolean; otherwise open to all. */
    public static GenderPreference resolveGenderPreference(GenderPreference explicit, Boolean legacyOpenToAll, AppUser host) {
        if (explicit != null) return explicit;
        return GenderPreference.fromLegacy(legacyOpenToAll, host != null ? host.getGender() : null);
    }

    public static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Maps an entity to its API shape. Must run inside a transaction (or with the host
     * already fetched) because {@code hostUser} is lazy and open-in-view is disabled.
     */
    public ListingResponse toResponse(Listing listing) {
        Point location = listing.getLocation();
        AppUser host = listing.getHostUser();

        String hostName = null;
        Integer hostAge = null;
        String hostAvatarUrl = null;
        String hostOccupationTitle = null;
        Boolean hostIsSeeker = null;
        Boolean hostIsVerified = null;
        if (host != null) {
            hostName = host.getFullName();
            hostAvatarUrl = host.getAvatarUrl();
            hostOccupationTitle = host.getOccupationTitle();
            hostIsSeeker = host.getIsLookingForRoom();
            hostIsVerified = Boolean.TRUE.equals(host.getIsIdentityVerified());
            if (host.getDateOfBirth() != null) {
                hostAge = Period.between(host.getDateOfBirth(), LocalDate.now()).getYears();
            }
        }

        return ListingResponse.builder()
                .id(listing.getId())
                .hostUserId(host != null ? host.getId() : null)
                .status(Boolean.TRUE.equals(listing.getIsAvailable()) ? "PUBLISHED" : "UNAVAILABLE")
                .listingType(listing.getListingType())
                .title(listing.getTitle())
                .rentAmount(listing.getRentAmount())
                .depositAmount(listing.getDepositAmount())
                .roomType(listing.getRoomType())
                .furnishingStatus(listing.getFurnishingStatus())
                .latitude(location != null ? location.getY() : null)
                .longitude(location != null ? location.getX() : null)
                .localityName(listing.getLocalityName())
                .city(listing.getCity())
                .stateName(listing.getStateName())
                .postalCode(listing.getPostalCode())
                .description(listing.getDescription())
                .photoUrls(listing.getPhotoUrls())
                .moveInDate(listing.getMoveInDate())
                .amenities(listing.getAmenities())
                .genderPreference(listing.getGenderPreference())
                .openToAllGenders(listing.isOpenToAllGenders())
                .isAvailable(listing.getIsAvailable())
                .kitchenType(listing.getKitchenType())
                .washroomType(listing.getWashroomType())
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .hostName(hostName)
                .hostAge(hostAge)
                .hostAvatarUrl(hostAvatarUrl)
                .hostOccupationTitle(hostOccupationTitle)
                .isRoomSeekerHost(hostIsSeeker)
                .isHostIdentityVerified(hostIsVerified)
                .build();
    }
}
