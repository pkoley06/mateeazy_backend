package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.dto.response.GifSearchResponse;
import com.matcher.matchmate_backend.service.GifService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** GIF picker backend for chat: proxies Tenor so the API key never ships in the app. */
@RestController
@RequestMapping("/api/v1/gifs")
public class GifController {

    private final GifService gifService;

    public GifController(GifService gifService) {
        this.gifService = gifService;
    }

    /** Lets the app hide the GIF button when no Tenor key is configured. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("enabled", gifService.isEnabled()));
    }

    /** Empty {@code q} returns the featured/trending set. */
    @GetMapping("/search")
    public ResponseEntity<GifSearchResponse> search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "24") int limit,
            @RequestParam(value = "pos", required = false) String pos
    ) {
        return ResponseEntity.ok(gifService.search(query, limit, pos));
    }
}
