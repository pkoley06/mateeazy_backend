package com.matcher.matchmate_backend.repository;

import java.util.UUID;

/**
 * Scalar-only projection for the discovery candidate query — avoids array/entity mapping in
 * native SQL. A row is either a room listing ({@link #KIND_LISTING}, id = listings.id) or a
 * user in Room Finder Mode ({@link #KIND_SEEKER}, id = app_users.id).
 */
public interface DiscoveryCandidateProjection {
    String KIND_LISTING = "LISTING";
    String KIND_SEEKER = "SEEKER";

    String getKind();

    UUID getCandidateId();

    Double getDistanceKm();
}
