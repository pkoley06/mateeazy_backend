package com.matcher.matchmate_backend.share;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Public pages behind shared links. nginx on mateeazy.in proxies
 * {@code /room/{id}} and {@code /post/{id}} here (see the website README), so the link
 * people share stays on the domain the Android app is verified for.
 *
 * <p>No authentication: these are read by WhatsApp / Telegram preview bots and by people
 * without the app. {@link SharePreviewService} decides what is safe to show.
 */
@RestController
@RequestMapping("/share")
public class SharePageController {

    private final SharePreviewService previews;
    private final SharePageRenderer renderer;

    public SharePageController(
            SharePreviewService previews,
            @Value("${app.share.site-base-url:https://mateeazy.in}") String siteBaseUrl) {
        this.previews = previews;
        this.renderer = new SharePageRenderer(siteBaseUrl);
    }

    @GetMapping({"/room/{id}", "/room/{id}/"})
    public ResponseEntity<String> room(@PathVariable String id) {
        return page(SharePreview.Kind.ROOM, id, previews::room);
    }

    @GetMapping({"/post/{id}", "/post/{id}/"})
    public ResponseEntity<String> post(@PathVariable String id) {
        return page(SharePreview.Kind.POST, id, previews::post);
    }

    private ResponseEntity<String> page(
            SharePreview.Kind kind, String rawId, Function<UUID, Optional<SharePreview>> lookup) {
        Optional<SharePreview> preview = parse(rawId).flatMap(lookup);
        return ResponseEntity.status(preview.isPresent() ? HttpStatus.OK : HttpStatus.NOT_FOUND)
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                // Short cache: a room taken down should stop previewing soon.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                // Users' rooms are shareable, not searchable.
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(preview.map(renderer::render).orElseGet(() -> renderer.renderMissing(kind)));
    }

    private static Optional<UUID> parse(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
