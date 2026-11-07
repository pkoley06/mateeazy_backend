package com.matcher.matchmate_backend.controller;

import com.matcher.matchmate_backend.security.AuthenticatedUserPrincipal;
import com.matcher.matchmate_backend.service.ChatMediaQuotaService;
import com.matcher.matchmate_backend.storage.ImageProcessingService;
import com.matcher.matchmate_backend.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uploads a profile photo or a listing's photos through the backend (not a presigned direct-
 * to-bucket upload), so {@link ImageProcessingService} can enforce the size budgets server-side
 * regardless of what the client already did. See the implementation plan for the trade-off.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private static final int PROFILE_PHOTO_MAX_DIMENSION = 1024;
    private static final int LISTING_PHOTO_MAX_DIMENSION = 1600;
    private static final int MAX_LISTING_PHOTOS = 10;

    private final StorageService storageService;
    private final ImageProcessingService imageProcessingService;

    @Value("${app.storage.max-profile-photo-bytes}")
    private long maxProfilePhotoBytes;

    @Value("${app.storage.max-listing-photos-total-bytes}")
    private long maxListingPhotosTotalBytes;

    @Value("${app.storage.max-chat-gif-bytes:5242880}")
    private long maxChatGifBytes;

    private final ChatMediaQuotaService mediaQuotaService;

    public UploadController(
            StorageService storageService,
            ImageProcessingService imageProcessingService,
            ChatMediaQuotaService mediaQuotaService
    ) {
        this.storageService = storageService;
        this.imageProcessingService = imageProcessingService;
        this.mediaQuotaService = mediaQuotaService;
    }

    @PostMapping("/profile-photo")
    public ResponseEntity<Map<String, String>> uploadProfilePhoto(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        byte[] compressed = imageProcessingService.recompress(
                readBytes(file), PROFILE_PHOTO_MAX_DIMENSION, maxProfilePhotoBytes);

        String key = "profile/" + principal.getUserId() + "/" + UUID.randomUUID() + ".jpg";
        StorageService.StoredObject stored = storageService.upload(key, compressed, "image/jpeg");

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", stored.url()));
    }

    @PostMapping("/listing-photos")
    public ResponseEntity<Map<String, List<String>>> uploadListingPhotos(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam("files") List<MultipartFile> files
    ) {
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }
        if (files.size() > MAX_LISTING_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_LISTING_PHOTOS + " photos per listing");
        }

        List<byte[]> raw = files.stream().map(UploadController::readBytes).toList();
        List<byte[]> compressed = imageProcessingService.recompressBatch(
                raw, LISTING_PHOTO_MAX_DIMENSION, maxListingPhotosTotalBytes);

        List<String> urls = compressed.stream()
                .map(bytes -> {
                    String key = "listings/" + principal.getUserId() + "/" + UUID.randomUUID() + ".jpg";
                    return storageService.upload(key, bytes, "image/jpeg").url();
                })
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("urls", urls));
    }

    /**
     * Chat photos: up to {@link ChatMediaQuotaService#DAILY_PHOTO_LIMIT} per message, the same
     * dimension / total-size budget as listing photos, and checked against today's allowance
     * before any bytes are stored. The quota is <em>consumed</em> only when the message is sent.
     */
    @PostMapping("/chat-images")
    public ResponseEntity<Map<String, List<String>>> uploadChatImages(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam("files") List<MultipartFile> files
    ) {
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }
        if (files.size() > ChatMediaQuotaService.DAILY_PHOTO_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most " + ChatMediaQuotaService.DAILY_PHOTO_LIMIT + " photos at a time");
        }
        mediaQuotaService.requireAllowance(principal.getUserId(), files.size());

        List<byte[]> raw = files.stream().map(UploadController::readBytes).toList();
        List<byte[]> compressed = imageProcessingService.recompressBatch(
                raw, LISTING_PHOTO_MAX_DIMENSION, maxListingPhotosTotalBytes);

        List<String> urls = compressed.stream()
                .map(bytes -> {
                    String key = "chat/" + principal.getUserId() + "/" + UUID.randomUUID() + ".jpg";
                    return storageService.upload(key, bytes, "image/jpeg").url();
                })
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("urls", urls));
    }

    /** A GIF from the user's gallery, stored as-is (re-encoding would drop the animation). */
    @PostMapping("/chat-gif")
    public ResponseEntity<Map<String, String>> uploadChatGif(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        byte[] bytes = readBytes(file);
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
        }
        if (bytes.length > maxChatGifBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "GIFs can be at most " + (maxChatGifBytes / (1024 * 1024)) + " MB");
        }
        if (!isGif(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only GIF files are accepted here");
        }
        String key = "chat/" + principal.getUserId() + "/" + UUID.randomUUID() + ".gif";
        StorageService.StoredObject stored = storageService.upload(key, bytes, "image/gif");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", stored.url()));
    }

    /** Magic-number check: "GIF87a" / "GIF89a". */
    private static boolean isGif(byte[] bytes) {
        return bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }
}
