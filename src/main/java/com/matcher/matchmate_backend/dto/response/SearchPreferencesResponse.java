package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchPreferencesResponse {

    private Double latitude;
    private Double longitude;
    private String locality;
    private Double searchRadiusKm;
    /** True when latitude/longitude are the user's saved alert area, false when they are a fallback (last known location / default city). */
    private Boolean hasTarget;
    private Boolean isLookingForRoom;
    private Boolean notifyRoomFinder;
    private Boolean notifyRoommateFinder;
}
