package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.DiscoveryCardResponse;
import com.matcher.matchmate_backend.dto.response.DiscoveryFeedResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.DiscoveryCandidateProjection;
import com.matcher.matchmate_backend.repository.ListingLikeRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Milestone 4 Step 4.2: joins the spatial candidate query ({@link ListingRepository}) with
 * {@link MatchingHeuristicEngine} to assemble the paginated discovery feed. See
 * "MatchMate Technical Architecture.md" §6.4 for the API contract this implements.
 * <p>
 * The feed mixes two kinds of card, both ordered by distance:
 * <ul>
 *   <li>room listings ({@code listingType} = the listing's own type, {@code listingId} set), and</li>
 *   <li>people in Room Finder Mode ({@code listingType} = {@code NEED_ROOM}, {@code listingId} null,
 *       no rent/room fields). They surface under the "All" and "Flatmates" segments only.</li>
 * </ul>
 */
@Service
public class DiscoveryService {

    private static final int MIN_RADIUS_KM = 2;
    private static final int MAX_RADIUS_KM = 50;

    private final ListingRepository listingRepository;
    private final AppUserRepository userRepository;
    private final MatchingHeuristicEngine matchingEngine;
    private final UserBlockRepository blockRepository;
    private final ListingLikeRepository likeRepository;

    public DiscoveryService(
            ListingRepository listingRepository,
            AppUserRepository userRepository,
            MatchingHeuristicEngine matchingEngine,
            UserBlockRepository blockRepository,
            ListingLikeRepository likeRepository
    ) {
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.matchingEngine = matchingEngine;
        this.blockRepository = blockRepository;
        this.likeRepository = likeRepository;
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
        // Room Finder Mode users are "looking for a room", so they belong to the Flatmates segment
        // (and to "All"); the Rooms segment stays listings-only.
        boolean includeSeekers = listingType == null || ListingType.NEED_ROOM.name().equals(listingType);

        // Room Finder Mode: only rooms open to the seeker's (chosen or own) gender. Off = no filter.
        String seekerGender = Boolean.TRUE.equals(seeker.getIsLookingForRoom())
                ? GenderPreference.sqlFilterValue(seeker.effectiveRoomFinderGender())
                : null;

        List<DiscoveryCandidateProjection> candidates = listingRepository.findCandidateIdsWithDistance(
                lat, lng, radiusMeters, currentUserId, gender, minAge, maxAge,
                smokingOnly, dietOnly, minRent, maxRent, listingType, includeSeekers, seekerGender,
                safeSize, safePage * safeSize);
        long totalElements = listingRepository.countCandidates(
                lat, lng, radiusMeters, currentUserId, gender, minAge, maxAge,
                smokingOnly, dietOnly, minRent, maxRent, listingType, includeSeekers, seekerGender);

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

    private List<DiscoveryCardResponse> buildCards(AppUser viewer, List<DiscoveryCandidateProjection> candidates) {
        if (candidates.isEmpty()) return List.of();

        List<UUID> listingIds = new ArrayList<>();
        List<UUID> seekerIds = new ArrayList<>();
        for (DiscoveryCandidateProjection row : candidates) {
            if (DiscoveryCandidateProjection.KIND_SEEKER.equals(row.getKind())) {
                seekerIds.add(row.getCandidateId());
            } else {
                listingIds.add(row.getCandidateId());
            }
        }

        Map<UUID, Listing> listingById = new HashMap<>();
        if (!listingIds.isEmpty()) {
            for (Listing listing : listingRepository.findAllWithHostByIdIn(listingIds)) {
                listingById.put(listing.getId(), listing);
            }
        }
        // One query for the whole page, so the client never asks per card.
        Set<UUID> likedListingIds = listingIds.isEmpty()
                ? Set.of()
                : new HashSet<>(likeRepository.findLikedListingIds(viewer.getId(), listingIds));
        Map<UUID, AppUser> seekerById = new HashMap<>();
        if (!seekerIds.isEmpty()) {
            for (AppUser user : userRepository.findAllById(seekerIds)) {
                seekerById.put(user.getId(), user);
            }
        }

        Set<UUID> blockedUserIds = new HashSet<>(blockRepository.findBlockedUserIdsByBlockerId(viewer.getId()));
        // Blocking is mutual in effect: people who blocked the viewer must not be shown to them either.
        blockedUserIds.addAll(blockRepository.findBlockerIdsByBlockedId(viewer.getId()));

        // Iterate the projection rows themselves so the ASC-by-distance order of the native query is kept
        // across both kinds of card.
        List<DiscoveryCardResponse> cards = new ArrayList<>();
        for (DiscoveryCandidateProjection row : candidates) {
            double distanceKm = roundToOneDecimal(row.getDistanceKm());
            if (DiscoveryCandidateProjection.KIND_SEEKER.equals(row.getKind())) {
                AppUser user = seekerById.get(row.getCandidateId());
                if (user == null || !isVisible(user, blockedUserIds)) continue;
                cards.add(seekerCard(viewer, user, distanceKm));
            } else {
                Listing listing = listingById.get(row.getCandidateId());
                if (listing == null) continue; // deleted between the two queries; skip rather than fail the request
                if (!isVisible(listing.getHostUser(), blockedUserIds)) continue;
                cards.add(listingCard(viewer, listing, distanceKm, likedListingIds.contains(listing.getId())));
            }
        }
        return cards;
    }

    /** Skip cards from blocked, suspended, or deactivated users. */
    private boolean isVisible(AppUser user, Set<UUID> blockedUserIds) {
        return !blockedUserIds.contains(user.getId())
                && !Boolean.TRUE.equals(user.getIsSuspended())
                && Boolean.TRUE.equals(user.getIsActive());
    }

    private DiscoveryCardResponse listingCard(AppUser viewer, Listing listing, double distanceKm, boolean likedByMe) {
        AppUser host = listing.getHostUser();
        MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(viewer, host);
        return personCard(host, match)
                .listingId(listing.getId())
                .title(listing.getTitle())
                .listingType(listing.getListingType())
                .roomType(listing.getRoomType())
                .furnishingStatus(listing.getFurnishingStatus())
                .rentAmount(listing.getRentAmount())
                .depositAmount(listing.getDepositAmount())
                .localityName(listing.getLocalityName())
                .city(listing.getCity())
                .genderPreference(listing.getGenderPreference())
                .distanceKm(distanceKm)
                .photoUrls(listing.getPhotoUrls())
                .likedByMe(likedByMe)
                .build();
    }

    /**
     * A person in Room Finder Mode has no listing row: the card carries only who they are and
     * where they are searching. {@code listingId} is null so clients route to the profile instead
     * of a room detail page.
     */
    private DiscoveryCardResponse seekerCard(AppUser viewer, AppUser seeker, double distanceKm) {
        MatchingHeuristicEngine.MatchResult match = matchingEngine.evaluate(viewer, seeker);
        String locality = seeker.getTargetSearchLocality() != null && !seeker.getTargetSearchLocality().isBlank()
                ? seeker.getTargetSearchLocality()
                : seeker.getLastKnownLocality();
        return personCard(seeker, match)
                .listingId(null)
                .title(null)
                .listingType(ListingType.NEED_ROOM)
                .localityName(locality != null && !locality.isBlank() ? locality : "Nearby")
                .distanceKm(distanceKm)
                .photoUrls(List.of())
                .build();
    }

    /** Fields shared by both card kinds: the person on the card plus their match with the viewer. */
    private DiscoveryCardResponse.DiscoveryCardResponseBuilder personCard(
            AppUser person, MatchingHeuristicEngine.MatchResult match) {
        int age = Period.between(person.getDateOfBirth(), LocalDate.now()).getYears();
        return DiscoveryCardResponse.builder()
                .hostUserId(person.getId())
                .hostName(person.getFullName())
                .hostAge(age)
                .hostGender(person.getGender())
                .occupationTitle(person.getOccupationTitle())
                .avatarUrl(person.getAvatarUrl())
                .matchPercentage(match.matchPercentage())
                .compatibilityHighlights(match.compatibilityHighlights());
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
