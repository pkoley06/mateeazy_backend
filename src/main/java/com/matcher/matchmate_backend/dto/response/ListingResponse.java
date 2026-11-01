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

/**
 * Shared response shape for create/get/list-mine. Superset of the §6.6 create-response
 * fields (id, hostUserId, status, createdAt) plus the full listing detail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingResponse {
    private UUID id;
    private UUID hostUserId;
    private String status; // "PUBLISHED" or "UNAVAILABLE", derived from isAvailable
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
    private GenderPreference genderPreference;
    /** Derived from {@link #genderPreference}; kept so older clients keep working. */
    private Boolean openToAllGenders;
    private Boolean isAvailable;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Host metadata, so Room Details can render the host card without
    // depending on a Discover card being passed through navigation.
    private String hostName;
    private Integer hostAge;
    private String hostAvatarUrl;
    private String hostOccupationTitle;
    private Boolean isRoomSeekerHost;
}
