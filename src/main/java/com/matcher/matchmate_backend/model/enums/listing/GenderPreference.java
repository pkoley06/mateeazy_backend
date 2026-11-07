package com.matcher.matchmate_backend.model.enums.listing;

import com.matcher.matchmate_backend.model.enums.auth.Gender;

/**
 * Who a host wants to share the room with. {@link #ALL} means open to every gender;
 * the other values mirror the selectable chips in the create-room wizard.
 */
public enum GenderPreference {
    ALL,
    MALE,
    FEMALE,
    NON_BINARY,
    OTHER;

    public boolean isOpenToAll() {
        return this == ALL;
    }

    /**
     * Bridges clients that still send the legacy {@code openToAllGenders} flag.
     * {@code true} (or unknown) maps to {@link #ALL}; {@code false} maps to the host's
     * own gender when it is one of the selectable preferences, otherwise {@link #ALL}.
     */
    public static GenderPreference fromLegacy(Boolean openToAllGenders, Gender hostGender) {
        if (openToAllGenders == null || openToAllGenders) return ALL;
        return fromGender(hostGender);
    }

    /** The preference value that matches a person of {@code gender}; {@link #ALL} when unknown. */
    public static GenderPreference fromGender(Gender gender) {
        if (gender == null) return ALL;
        return switch (gender) {
            case MALE -> MALE;
            case FEMALE -> FEMALE;
            case NON_BINARY -> NON_BINARY;
            case OTHERS -> OTHER;
        };
    }

    /**
     * Room Finder Mode gender filter: what a seeker has chosen, falling back to their own
     * gender when they never picked one. A listing is shown to the seeker when its
     * {@code genderPreference} is {@link #ALL} or equals this value; {@link #ALL} here
     * means "show me every room".
     */
    public static GenderPreference effectiveRoomFinderGender(GenderPreference chosen, Gender ownGender) {
        return chosen != null ? chosen : fromGender(ownGender);
    }

    /** SQL-side form of {@link #effectiveRoomFinderGender}: null when no filtering applies. */
    public static String sqlFilterValue(GenderPreference effective) {
        return effective == null || effective == ALL ? null : effective.name();
    }
}
