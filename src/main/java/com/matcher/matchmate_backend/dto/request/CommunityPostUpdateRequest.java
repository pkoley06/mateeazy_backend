package com.matcher.matchmate_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
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
public class CommunityPostUpdateRequest {

    @NotBlank(message = "Title / caption is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Content / description is required")
    @Size(max = 5000, message = "Content cannot exceed 5000 characters")
    private String content;

    private List<String> imageUrls;

    private UUID taggedListingId;
}
