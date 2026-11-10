package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.response.DiscoveryFeedResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.DiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Milestone 4 Step 4.3. See "MatchMate Technical Architecture.md" §6.4 for the full contract. */
@RestController
@RequestMapping("/api/v1/discovery")
public class DiscoveryController {

    // Default fallback coordinates: central Kolkata (see architecture doc §3.4/§6.4).
    private static final double DEFAULT_LAT = 22.5726;
    private static final double DEFAULT_LNG = 88.3639;

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public ResponseEntity<DiscoveryFeedResponse> getFeed(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "" + DEFAULT_LAT) double lat,
            @RequestParam(defaultValue = "" + DEFAULT_LNG) double lng,
            @RequestParam(defaultValue = "10") int radiusKm,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) String smoking,
            @RequestParam(required = false) String diet,
            @RequestParam(required = false) Integer minRent,
            @RequestParam(required = false) Integer maxRent,
            @RequestParam(required = false) String listingType,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) String furnishingStatus,
            @RequestParam(defaultValue = "DISTANCE") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DiscoveryFeedResponse response = discoveryService.getFeed(
                principal.getUserId(), lat, lng, radiusKm, gender, minAge, maxAge, smoking, diet,
                minRent, maxRent, listingType, roomType, furnishingStatus, sortBy, page, size);
        return ResponseEntity.ok(response);
    }
}
