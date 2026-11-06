package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Summary row for GET /api/v1/admin/listings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminListingSummaryResponse {
    private UUID id;
    private String title;
    private UUID hostUserId;
    private String hostUserName;
    private String hostAvatarUrl;
    private ListingType listingType;
    private RoomType roomType;
    private FurnishingStatus furnishingStatus;
    private Integer rentAmount;
    private Integer depositAmount;
    private String localityName;
    private String city;
    private String stateName;
    private String postalCode;
    private GenderPreference genderPreference;
    private Boolean isAvailable;
    private List<String> photoUrls;
    private OffsetDateTime createdAt;
}
