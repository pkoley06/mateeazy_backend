package com.matcher.matchmate_backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where the user's device actually is - the profile's home area ({@code last_known_*}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceLocationRequest {

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    /** Reverse-geocoded label ("Salt Lake, Kolkata"); blank leaves the stored one untouched. */
    private String locality;
}
