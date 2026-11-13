package com.matcher.matchmate_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityCommentCreateRequest {
    @NotBlank(message = "Comment content cannot be empty")
    private String content;
}
