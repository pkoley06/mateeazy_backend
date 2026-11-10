package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed payload for GET /api/v1/admin/listings/{id}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminListingDetailResponse {
    private UUID id;
    private UUID hostUserId;
    private String hostUserName;
    private String hostAvatarUrl;
    private ListingType listingType;
    private String title;
    private Integer rentAmount;
    private Integer depositAmount;
    private RoomType roomType;
    private FurnishingStatus furnishingStatus;
    private Double latitude;
    private Double longitude;
    private String localityName;
    private String city;
    private String stateName;
    private String postalCode;
    private String description;
    private List<String> photoUrls;
    private LocalDate moveInDate;
    private List<String> amenities;
    private List<String> houseRules;
    private GenderPreference genderPreference;
    /** Derived from {@link #genderPreference}. */
    private Boolean openToAllGenders;
    private Boolean isAvailable;
    private long likesCount;
    private long commentsCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
