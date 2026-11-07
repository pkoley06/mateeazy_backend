package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One GIF from the picker: the full-size URL to send and a small preview for the grid. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GifResponse {
    private String id;
    private String url;
    private String previewUrl;
    private int width;
    private int height;
}
