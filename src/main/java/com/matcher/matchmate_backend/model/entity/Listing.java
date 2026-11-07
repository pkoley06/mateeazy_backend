package com.matcher.matchmate_backend.model.entity;

import com.matcher.matchmate_backend.model.enums.listing.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private AppUser hostUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 20)
    private ListingType listingType;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "rent_amount", nullable = false)
    private Integer rentAmount;

    @Column(name = "deposit_amount", nullable = false)
    private Integer depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "furnishing_status", nullable = false, length = 20)
    private FurnishingStatus furnishingStatus;

    @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "locality_name", nullable = false, length = 150)
    private String localityName;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** Reverse-geocoded administrative area (state / province); optional. */
    @Column(name = "state_name", length = 100)
    private String stateName;

    /** Reverse-geocoded postal / PIN code; optional. */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_urls", columnDefinition = "text[]")
    @Builder.Default
    private List<String> photoUrls = new ArrayList<>();

    @Column(name = "move_in_date")
    private LocalDate moveInDate;

    @Column(name = "amenities", columnDefinition = "text[]")
    @Builder.Default
    private List<String> amenities = new ArrayList<>();

    /** Replaces the V4 {@code open_to_all_genders} flag (migrated in V12). */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_preference", nullable = false, length = 20)
    @Builder.Default
    private GenderPreference genderPreference = GenderPreference.ALL;

    /** Shared or private kitchen (V19); null on rooms listed before the question existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "kitchen_type", length = 20)
    private FacilitySharing kitchenType;

    /** Shared or private washroom (V19); null on rooms listed before the question existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "washroom_type", length = 20)
    private FacilitySharing washroomType;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Derived view kept for API compatibility with clients that still read the boolean. */
    @Transient
    public boolean isOpenToAllGenders() {
        return genderPreference == null || genderPreference.isOpenToAll();
    }
}