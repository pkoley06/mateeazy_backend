package com.matcher.matchmate_backend.controller;


import com.matcher.matchmate_backend.dto.request.DemographicsRequest;
import com.matcher.matchmate_backend.dto.request.DeviceLocationRequest;
import com.matcher.matchmate_backend.dto.request.LifestyleRequest;
import com.matcher.matchmate_backend.dto.request.RoomSeekerRequest;
import com.matcher.matchmate_backend.dto.response.OtherUserProfileResponse;
import com.matcher.matchmate_backend.dto.response.UserDemographicsResponse;
import com.matcher.matchmate_backend.dto.response.UserProfileResponse;
import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/me/demographics")
    public ResponseEntity<UserDemographicsResponse> updateDemographics(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody DemographicsRequest request
            ){
        UserDemographicsResponse response = userService.updateDemographics(principal.getUserId(), request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/lifestyle")
    public ResponseEntity<UserProfileResponse> updateLifestyle(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody LifestyleRequest request
    ) {
        return ResponseEntity.ok(userService.updateLifestyle(principal.getUserId(), request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(userService.getMyProfile(principal.getUserId()));
    }

    /**
     * The app reports where the device is once location access is granted, so the profile's
     * home area is the user's real one instead of the "Kolkata" default.
     */
    @PutMapping("/me/location")
    public ResponseEntity<Void> updateMyLocation(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody DeviceLocationRequest request
    ) {
        userService.updateDeviceLocation(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OtherUserProfileResponse> getOtherUserProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getOtherUserProfile(id));
    }

    /**
     * Room Finder Mode: body {@code {"is_room_seeker": true|false}} and/or
     * {@code {"room_finder_gender": "ALL"|"MALE"|"FEMALE"|"NON_BINARY"|"OTHER"}}.
     */
    @PatchMapping("/me/room-seeker")
    public ResponseEntity<UserProfileResponse> updateRoomSeeker(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @RequestBody RoomSeekerRequest request
    ) {
        return ResponseEntity.ok(userService.updateRoomSeeker(
                principal.getUserId(), request.getIsRoomSeeker(), request.getRoomFinderGender()));
    }

    /**
     * Settings → Account → Disable account. Soft, reversible: the account is hidden everywhere and
     * every existing session token stops working until the user signs in again.
     */
    @PostMapping("/me/disable")
    public ResponseEntity<Void> disableMyAccount(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userService.disableMyAccount(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userService.deleteMyAccount(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Settings → Account → pending-deletion confirm screen → "Keep my account". Only callable
     * while the account is PENDING_DELETION - see JwtAuthenticationFilter for how such a user's
     * token is still accepted for this one route while every other route stays blocked.
     */
    @PostMapping("/me/cancel-deletion")
    public ResponseEntity<Void> cancelDeletion(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        userService.cancelDeletion(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

}
