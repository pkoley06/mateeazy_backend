package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.Gender;
import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * One card in the discovery feed — see "MatchMate Technical Architecture.md" §6.4.
 * <p>
 * Either a room listing or a person in Room Finder Mode. For a person, {@code listingType} is
 * {@code NEED_ROOM} and {@code listingId}, {@code title}, {@code roomType}, {@code furnishingStatus},
 * {@code rentAmount}, {@code depositAmount}, {@code city} and {@code genderPreference} are null;
 * {@code hostUserId} is that person's user id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryCardResponse {
    /** Null for a Room Finder Mode card (no listing row exists). */
    private UUID listingId;
    private String title;
    /** The host of the listing, or the person seeking a room. */
    private UUID hostUserId;
    private String hostName;
    private Integer hostAge;
    private Gender hostGender;
    private String occupationTitle;
    private String avatarUrl;
    private ListingType listingType;
    private RoomType roomType;
    private FurnishingStatus furnishingStatus;
    private Integer rentAmount;
    private Integer depositAmount;
    private String localityName;
    private String city;
    private GenderPreference genderPreference;
    private Double distanceKm;
    /** Null when either side hasn't finished the lifestyle questionnaire yet. */
    private Integer matchPercentage;
    private List<String> compatibilityHighlights;
    private List<String> photoUrls;
}
