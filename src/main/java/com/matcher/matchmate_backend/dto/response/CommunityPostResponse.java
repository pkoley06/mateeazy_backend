package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPostResponse {
    private UUID id;
    private CommunityPostCategory category;
    private String title;
    private String content;
    private Integer priceAmount;
    private Double latitude;
    private Double longitude;
    private String localityName;
    private String city;
    private Double distanceKm;
    private List<String> imageUrls;
    private Integer likesCount;
    private Integer commentsCount;
    private Boolean isLikedByMe;
    private Boolean isPinned;
    private CommunityPostStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Author metadata
    private UUID authorId;
    private String authorName;
    private String authorAvatarUrl;
    private Boolean authorIsVerified;
    private String authorOccupation;
}
