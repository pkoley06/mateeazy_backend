package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.listing.FacilitySharing;
import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** POST /api/v1/listings request body — see "MatchMate Technical Architecture.md" §6.6. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingCreateRequest {

    @NotNull(message = "Listing type is required")
    private ListingType listingType;

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @NotNull(message = "Rent amount is required")
    @Min(value = 0, message = "Rent amount cannot be negative")
    private Integer rentAmount;

    @NotNull(message = "Deposit amount is required")
    @Min(value = 0, message = "Deposit amount cannot be negative")
    private Integer depositAmount;

    @NotNull(message = "Room type is required")
    private RoomType roomType;

    @NotNull(message = "Furnishing status is required")
    private FurnishingStatus furnishingStatus;

    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;

    @NotBlank(message = "Locality name is required")
    @Size(max = 150, message = "Locality name cannot exceed 150 characters")
    private String localityName;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    /** Reverse-geocoded state / province (optional). */
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String stateName;

    /** Reverse-geocoded postal / PIN code (optional). */
    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    /** Already-uploaded photo URLs, obtained from POST /api/v1/uploads/listing-photos. */
    private List<String> photoUrls;

    private LocalDate moveInDate;

    private List<String> amenities;

    /** ALL / MALE / FEMALE / NON_BINARY. Defaults to ALL (or to the legacy flag below) when omitted. */
    private GenderPreference genderPreference;

    /** Legacy flag kept for older clients; ignored when {@link #genderPreference} is sent. */
    private Boolean openToAllGenders;

    /** SHARED / PRIVATE kitchen; optional. */
    private FacilitySharing kitchenType;

    /** SHARED / PRIVATE washroom; optional. */
    private FacilitySharing washroomType;
}
