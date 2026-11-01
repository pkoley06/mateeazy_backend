package com.matcher.matchmate_backend.repository;

import java.util.UUID;

/** Scalar-only projection for the spatial candidate query — avoids array/entity mapping in native SQL. */
public interface ListingDistanceProjection {
    UUID getListingId();

    Double getDistanceKm();
}
