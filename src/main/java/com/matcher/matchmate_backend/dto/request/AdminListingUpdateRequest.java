package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminListingUpdateRequest {

    private ListingType listingType;

    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @Min(value = 0, message = "Rent amount cannot be negative")
    private Integer rentAmount;

    @Min(value = 0, message = "Deposit amount cannot be negative")
    private Integer depositAmount;

    private RoomType roomType;
    private FurnishingStatus furnishingStatus;

    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;

    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;

    @Size(max = 150, message = "Locality name cannot exceed 150 characters")
    private String localityName;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String stateName;

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    private List<String> photoUrls;
    private LocalDate moveInDate;
    private List<String> amenities;
    private List<String> houseRules;
    private GenderPreference genderPreference;
    /** Legacy flag; only consulted when {@link #genderPreference} is absent. */
    private Boolean openToAllGenders;
    private Boolean isAvailable;
}
