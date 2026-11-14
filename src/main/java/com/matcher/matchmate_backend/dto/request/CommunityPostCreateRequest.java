package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostCreateRequest {

    @NotNull(message = "Category is required")
    private CommunityPostCategory category;

    @NotBlank(message = "Title is required")
    @Size(max = 160, message = "Title cannot exceed 160 characters")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    private Integer priceAmount;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    @NotBlank(message = "Locality name is required")
    @Size(max = 150)
    private String localityName;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    private List<String> imageUrls;

    private UUID taggedListingId;
}
