package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.DiscoveryCardResponse;
import com.matcher.matchmate_backend.dto.response.DiscoveryFeedResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingDistanceProjection;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Milestone 4 Step 4.2: joins the spatial candidate query ({@link ListingRepository}) with
 * {@link MatchingHeuristicEngine} to assemble the paginated discovery feed. See
 * "MatchMate Technical Architecture.md" §6.4 for the API contract this implements.
 */
@Service
public class DiscoveryService {

    private static final int MIN_RADIUS_KM = 2;
    private static final int MAX_RADIUS_KM = 50;

    private final ListingRepository listingRepository;
    private final AppUserRepository userRepository;
    private final MatchingHeuristicEngine matchingEngine;
    private final UserBlockRepository blockRepository;

    public DiscoveryService(
            ListingRepository listingRepository,
            AppUserRepository userRepository,
            MatchingHeuristicEngine matchingEngine,
            UserBlockRepository blockRepository
    ) {
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.matchingEngine = matchingEngine;
        this.blockRepository = blockRepository;
    }

    public DiscoveryFeedResponse getFeed(
            UUID currentUserId,
            double lat,
            double lng,
            int radiusKm,
            String genderParam,
            Integer minAge,
            Integer maxAge,
            String smokingParam,
            String dietParam,
            Integer minRent,
            Integer maxRent,
            String listingTypeParam,
            int page,
            int size
    ) {
        AppUser seeker = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        int clampedRadiusKm = Math.clamp(radiusKm, MIN_RADIUS_KM, MAX_RADIUS_KM);
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 50);
        double radiusMeters = clampedRadiusKm * 1000.0;
        String gender = normalizeGender(genderParam);
        boolean smokingOnly = "NON_SMOKER_ONLY".equalsIgnoreCase(smokingParam);
        boolean dietOnly = "VEGETARIAN_ONLY".equalsIgnoreCase(dietParam);
        String listingType = normalizeListingType(listingTypeParam);

        List<ListingDistanceProjection> candidates = listingRepository.findCandidateIdsWithDistance(
                lat, lng, radiusMeters, currentUserId, gender, minAge, maxAge,
                smokingOnly, dietOnly, minRent, maxRent, listingType, safeSize, safePage * safeSize);
        long totalElements = listingRepository.countCandidates(
                lat, lng, radiusMeters, currentUserId, gender, minAge, maxAge,
                smokingOnly, dietOnly, minRent, maxRent, listingType);

        List<DiscoveryCardResponse> content = buildCards(seeker, candidates);

        int totalPages = safeSize > 0 ? (int) Math.ceil((double) totalElements / safeSize) : 0;
        boolean isLast = totalPages == 0 || safePage + 1 >= totalPages;

        return DiscoveryFeedResponse.builder()
                .content(content)
                .page(safePage)
                .size(safeSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .isLast(isLast)
                .build();
    }

    private List<DiscoveryCardResponse> buildCards(AppUser seeker, List<ListingDistanceProjection> candidates) {
        if (candidates.isEmpty()) return List.of();

        // LinkedHashMap preserves the ASC-by-distance order the native query already returned.
        Map<UUID, Double> distanceByListingId = new LinkedHashMap<>();
        for (ListingDistanceProjection row : candidates) {
            distanceByListingId.put(row.getListingId(), row.getDistanceKm());
        }

        List<Listing> listings = listingRepository.findAllWithHostByIdIn(List.copyOf(distanceByListingId.keySet()));
        Map<UUID, Listing> listingById = new HashMap<>();
        for (Listing listing : listings) {
            listingById.put(listing.getId(), listing);
        }

        Set<UUID> blockedUserIds = new java.util.HashSet<>(blockRepository.findBlockedUserIdsByBlockerId(seeker.getId()));
        // Blocking is mutual in effect: hosts who blocked the seeker must not see them either.
        blockedUserIds.addAll(blockRepository.findBlockerIdsByBlockedId(seeker.getId()));

        List<DiscoveryCardResponse> cards = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Double> entry : distanceByListingId.entrySet()) {
            Listing listing = listingById.get(entry.getKey());
            if (listing == null) continue; // deleted between the two queries; skip rather than fail the request

            AppUser host = listing.getHostUser();
            if (blockedUserIds.contains(host.getId()) ||
                    Boolean.TRUE.equals(host.getIsSuspended()) ||
                    !Boolean.TRUE.equals(host.getIsActive())) {
                continue; // Skip listings from blocked, suspended, or deactivated users
            }
            int hostAge = Period.between(host.getDateOfBirth(), LocalDate.now()).getYears();
            MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(seeker, host);

            cards.add(DiscoveryCardResponse.builder()
                    .listingId(listing.getId())
                    .title(listing.getTitle())
                    .hostUserId(host.getId())
                    .hostName(host.getFullName())
                    .hostAge(hostAge)
                    .hostGender(host.getGender())
                    .occupationTitle(host.getOccupationTitle())
                    .avatarUrl(host.getAvatarUrl())
                    .listingType(listing.getListingType())
                    .roomType(listing.getRoomType())
                    .furnishingStatus(listing.getFurnishingStatus())
                    .rentAmount(listing.getRentAmount())
                    .depositAmount(listing.getDepositAmount())
                    .localityName(listing.getLocalityName())
                    .city(listing.getCity())
                    .genderPreference(listing.getGenderPreference())
                    .distanceKm(roundToOneDecimal(entry.getValue()))
                    .matchPercentage(match.matchPercentage())
                    .compatibilityHighlights(match.compatibilityHighlights())
                    .photoUrls(listing.getPhotoUrls())
                    .build());
        }
        return cards;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private String normalizeGender(String genderParam) {
        if (genderParam == null || genderParam.isBlank() || "ANY".equalsIgnoreCase(genderParam)) {
            return null;
        }
        try {
            return Gender.valueOf(genderParam.toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid gender filter: " + genderParam);
        }
    }

    private String normalizeListingType(String listingTypeParam) {
        if (listingTypeParam == null || listingTypeParam.isBlank() || "ANY".equalsIgnoreCase(listingTypeParam)) {
            return null;
        }
        try {
            return com.matcher.matchmate_backend.model.enums.listing.ListingType.valueOf(listingTypeParam.toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid listingType filter: " + listingTypeParam);
        }
    }
}
