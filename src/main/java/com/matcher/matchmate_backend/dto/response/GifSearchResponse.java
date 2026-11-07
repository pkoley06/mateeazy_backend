package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GifSearchResponse {
    private List<GifResponse> results;
    /** Opaque cursor for the next page; null when exhausted. */
    private String next;
}
