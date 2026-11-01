package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.AdminListingCreateRequest;
import com.matcher.matchmate_backend.dto.request.AdminListingUpdateRequest;
import com.matcher.matchmate_backend.dto.response.AdminListingDetailResponse;
import com.matcher.matchmate_backend.dto.response.AdminListingSummaryResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import jakarta.transaction.Transactional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.UUID;

@Service
public class AdminListingService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ListingRepository listingRepository;
    private final AppUserRepository userRepository;
    private final NotificationService notificationService;

    public AdminListingService(
            ListingRepository listingRepository,
            AppUserRepository userRepository,
            NotificationService notificationService
    ) {
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public PageResponse<AdminListingSummaryResponse> list(String search, Boolean isAvailable, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String query = StringUtils.hasText(search) ? search.trim() : null;
        Page<Listing> result = listingRepository.searchAdminListings(query, isAvailable, pageable);

        return PageResponse.of(result, this::toSummary);
    }

    public AdminListingDetailResponse getById(UUID id) {
        return toDetail(findListing(id));
    }

    @Transactional
    public AdminListingDetailResponse create(AdminListingCreateRequest request) {
        AppUser host = userRepository.findById(request.getHostUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Host user not found"));

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
                .stateName(ListingService.trimToNull(request.getStateName()))
                .postalCode(ListingService.trimToNull(request.getPostalCode()))
                .description(request.getDescription())
                .photoUrls(request.getPhotoUrls() != null ? new ArrayList<>(request.getPhotoUrls()) : new ArrayList<>())
                .moveInDate(request.getMoveInDate())
                .amenities(request.getAmenities() != null ? new ArrayList<>(request.getAmenities()) : new ArrayList<>())
                .genderPreference(ListingService.resolveGenderPreference(request.getGenderPreference(), request.getOpenToAllGenders(), host))
                .isAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true)
                .build();

        Listing saved = listingRepository.save(listing);
        notificationService.notifyMatchingSeekersForNewListing(saved);
        return toDetail(saved);
    }

    @Transactional
    public AdminListingDetailResponse update(UUID id, AdminListingUpdateRequest request) {
        Listing listing = findListing(id);

        if (request.getListingType() != null) listing.setListingType(request.getListingType());
        if (StringUtils.hasText(request.getTitle())) listing.setTitle(request.getTitle());
        if (request.getRentAmount() != null) listing.setRentAmount(request.getRentAmount());
        if (request.getDepositAmount() != null) listing.setDepositAmount(request.getDepositAmount());
        if (request.getRoomType() != null) listing.setRoomType(request.getRoomType());
        if (request.getFurnishingStatus() != null) listing.setFurnishingStatus(request.getFurnishingStatus());
        if (request.getLatitude() != null && request.getLongitude() != null) {
            listing.setLocation(toPoint(request.getLatitude(), request.getLongitude()));
        }
        if (StringUtils.hasText(request.getLocalityName())) listing.setLocalityName(request.getLocalityName());
        if (StringUtils.hasText(request.getCity())) listing.setCity(request.getCity());
        if (request.getStateName() != null) listing.setStateName(ListingService.trimToNull(request.getStateName()));
        if (request.getPostalCode() != null) listing.setPostalCode(ListingService.trimToNull(request.getPostalCode()));
        if (request.getDescription() != null) listing.setDescription(request.getDescription());
        if (request.getPhotoUrls() != null) listing.setPhotoUrls(new ArrayList<>(request.getPhotoUrls()));
        if (request.getMoveInDate() != null) listing.setMoveInDate(request.getMoveInDate());
        if (request.getAmenities() != null) listing.setAmenities(new ArrayList<>(request.getAmenities()));
        if (request.getGenderPreference() != null) {
            listing.setGenderPreference(request.getGenderPreference());
        } else if (request.getOpenToAllGenders() != null) {
            listing.setGenderPreference(GenderPreference.fromLegacy(request.getOpenToAllGenders(), listing.getHostUser().getGender()));
        }
        if (request.getIsAvailable() != null) listing.setIsAvailable(request.getIsAvailable());

        Listing saved = listingRepository.save(listing);
        notificationService.notifyMatchingSeekersForNewListing(saved);
        return toDetail(saved);
    }

    @Transactional
    public AdminListingDetailResponse setAvailable(UUID id, boolean available) {
        Listing listing = findListing(id);
        listing.setIsAvailable(available);
        Listing saved = listingRepository.save(listing);
        notificationService.notifyMatchingSeekersForNewListing(saved);
        return toDetail(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Listing listing = findListing(id);
        listingRepository.delete(listing);
    }

    private Listing findListing(UUID id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    private Point toPoint(double latitude, double longitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private AdminListingSummaryResponse toSummary(Listing listing) {
        return AdminListingSummaryResponse.builder()
                .id(listing.getId())
                .title(listing.getTitle())
                .hostUserId(listing.getHostUser().getId())
                .hostUserName(listing.getHostUser().getFullName())
                .listingType(listing.getListingType())
                .roomType(listing.getRoomType())
                .furnishingStatus(listing.getFurnishingStatus())
                .rentAmount(listing.getRentAmount())
                .depositAmount(listing.getDepositAmount())
                .localityName(listing.getLocalityName())
                .city(listing.getCity())
                .stateName(listing.getStateName())
                .postalCode(listing.getPostalCode())
                .genderPreference(listing.getGenderPreference())
                .isAvailable(listing.getIsAvailable())
                .photoUrls(listing.getPhotoUrls())
                .createdAt(listing.getCreatedAt())
                .build();
    }

    private AdminListingDetailResponse toDetail(Listing listing) {
        Point pt = listing.getLocation();
        return AdminListingDetailResponse.builder()
                .id(listing.getId())
                .hostUserId(listing.getHostUser().getId())
                .hostUserName(listing.getHostUser().getFullName())
                .hostAvatarUrl(listing.getHostUser().getAvatarUrl())
                .listingType(listing.getListingType())
                .title(listing.getTitle())
                .rentAmount(listing.getRentAmount())
                .depositAmount(listing.getDepositAmount())
                .roomType(listing.getRoomType())
                .furnishingStatus(listing.getFurnishingStatus())
                .latitude(pt != null ? pt.getY() : null)
                .longitude(pt != null ? pt.getX() : null)
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
                .createdAt(listing.getCreatedAt())
                .updatedAt(listing.getUpdatedAt())
                .build();
    }
}
