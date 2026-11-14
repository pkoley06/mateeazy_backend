package com.matcher.matchmate_backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusCreateRequest {
    private String mediaUrl;
    private String caption;
    private String backgroundColor;
}
