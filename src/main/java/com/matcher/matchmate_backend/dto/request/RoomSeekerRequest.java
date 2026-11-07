package com.matcher.matchmate_backend.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.matcher.matchmate_backend.model.enums.listing.GenderPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PATCH /api/v1/users/me/room-seeker — flips the "actively looking for a room" flag from the
 * Discover header / Settings toggle, and/or sets the Room Finder gender filter. Both fields
 * are optional so either can be changed on its own; at least one must be present. Accepts
 * {@code is_room_seeker} (the documented key) as well as the camelCase {@code isRoomSeeker}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomSeekerRequest {

    @JsonProperty("is_room_seeker")
    @JsonAlias({"isRoomSeeker", "is_looking_for_room", "isLookingForRoom"})
    private Boolean isRoomSeeker;

    /** Which rooms to show while Room Finder Mode is on; {@code ALL} = every room. */
    @JsonProperty("room_finder_gender")
    @JsonAlias({"roomFinderGender"})
    private GenderPreference roomFinderGender;

    public boolean isEmpty() {
        return isRoomSeeker == null && roomFinderGender == null;
    }
}
