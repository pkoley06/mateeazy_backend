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

/** One card in the discovery feed — see "MatchMate Technical Architecture.md" §6.4. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryCardResponse {
    private UUID listingId;
    private String title;
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
