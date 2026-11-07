package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.GifResponse;
import com.matcher.matchmate_backend.dto.response.GifSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin proxy over the Tenor v2 API so the mobile app never holds the key. Configured with
 * {@code app.gifs.tenor-api-key}; when blank the picker is reported as disabled and search
 * answers 503, so the app can hide / explain the GIF button instead of failing silently.
 */
@Service
public class GifService {

    private static final Logger log = LoggerFactory.getLogger(GifService.class);
    private static final String TENOR_BASE = "https://tenor.googleapis.com/v2";
    private static final String CLIENT_KEY = "mateeazy";
    private static final int MAX_LIMIT = 40;

    private final RestClient restClient = RestClient.builder().baseUrl(TENOR_BASE).build();

    @Value("${app.gifs.tenor-api-key:}")
    private String tenorApiKey;

    public boolean isEnabled() {
        return tenorApiKey != null && !tenorApiKey.isBlank();
    }

    /** Only URLs from Tenor's own CDN are accepted as GIF attachments that weren't uploaded by us. */
    public static boolean isTrustedGifUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("https://media.tenor.com/") || lower.startsWith("https://c.tenor.com/");
    }

    public GifSearchResponse search(String query, int limit, String pos) {
        requireEnabled();
        String q = query == null ? "" : query.trim();
        String path = q.isEmpty() ? "/featured" : "/search";
        return fetch(path, q, limit, pos);
    }

    @SuppressWarnings("unchecked")
    private GifSearchResponse fetch(String path, String query, int limit, String pos) {
        int safeLimit = Math.clamp(limit, 1, MAX_LIMIT);
        try {
            Map<String, Object> body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path)
                                .queryParam("key", tenorApiKey)
                                .queryParam("client_key", CLIENT_KEY)
                                .queryParam("limit", safeLimit)
                                .queryParam("media_filter", "gif,tinygif")
                                .queryParam("contentfilter", "medium");
                        if (!query.isEmpty()) uriBuilder.queryParam("q", query);
                        if (pos != null && !pos.isBlank()) uriBuilder.queryParam("pos", pos);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(Map.class);
            List<GifResponse> gifs = new ArrayList<>();
            Object results = body == null ? null : body.get("results");
            if (results instanceof List<?> list) {
                for (Object item : list) {
                    GifResponse gif = toGif(item);
                    if (gif != null) gifs.add(gif);
                }
            }
            String next = body == null ? null : String.valueOf(body.getOrDefault("next", ""));
            return GifSearchResponse.builder().results(gifs).next(next == null || next.isBlank() ? null : next).build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[GIF] Tenor request failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GIF search is unavailable right now");
        }
    }

    @SuppressWarnings("unchecked")
    private static GifResponse toGif(Object item) {
        if (!(item instanceof Map<?, ?> map)) return null;
        Object formats = map.get("media_formats");
        if (!(formats instanceof Map<?, ?> formatMap)) return null;
        Map<String, Object> gif = (Map<String, Object>) formatMap.get("gif");
        Map<String, Object> tiny = (Map<String, Object>) formatMap.get("tinygif");
        if (gif == null) return null;
        String url = String.valueOf(gif.get("url"));
        if (!isTrustedGifUrl(url)) return null;
        Map<String, Object> preview = tiny != null ? tiny : gif;
        return GifResponse.builder()
                .id(String.valueOf(map.get("id")))
                .url(url)
                .previewUrl(String.valueOf(preview.get("url")))
                .width(dimension(preview, 0))
                .height(dimension(preview, 1))
                .build();
    }

    private static int dimension(Map<String, Object> format, int index) {
        Object dims = format.get("dims");
        if (dims instanceof List<?> list && list.size() > index && list.get(index) instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GIF search isn't set up on this server yet");
        }
    }
}
