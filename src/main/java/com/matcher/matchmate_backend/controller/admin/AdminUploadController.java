package com.matcher.matchmate_backend.controller.admin;

import com.matcher.matchmate_backend.storage.ImageProcessingService;
import com.matcher.matchmate_backend.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Image uploads from the admin console, into the same bucket (and under the same key layout)
 * the mobile app uses - see {@link com.matcher.matchmate_backend.controller.UploadController}.
 * Protected by the admin filter chain (ROLE_ADMIN).
 *
 * <p>Every endpoint only stores the image and returns its public URL; the caller then saves
 * that URL on the user / listing / branding record through the regular admin endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/uploads")
public class AdminUploadController {

    private static final int PROFILE_PHOTO_MAX_DIMENSION = 1024;
    private static final int LISTING_PHOTO_MAX_DIMENSION = 1600;
    private static final int MAX_LISTING_PHOTOS = 10;

    /** Splash / login artwork is shown full-screen, so it gets a larger budget than a photo. */
    private static final int SPLASH_MAX_DIMENSION = 2048;
    private static final long SPLASH_MAX_BYTES = 800 * 1024;

    /** Logo and payment QR stay lossless PNG (transparency / scannable edges). */
    private static final int ARTWORK_MAX_DIMENSION = 1024;

    /** Key folder used when the photo is not yet tied to a user (e.g. while creating one). */
    private static final String UNASSIGNED_OWNER = "admin";

    private final StorageService storageService;
    private final ImageProcessingService imageProcessingService;

    @Value("${app.storage.max-profile-photo-bytes}")
    private long maxProfilePhotoBytes;

    @Value("${app.storage.max-listing-photos-total-bytes}")
    private long maxListingPhotosTotalBytes;

    public AdminUploadController(StorageService storageService, ImageProcessingService imageProcessingService) {
        this.storageService = storageService;
        this.imageProcessingService = imageProcessingService;
    }

    /** A profile photo for {@code userId} (optional while the user is still being created). */
    @PostMapping("/profile-photo")
    public ResponseEntity<Map<String, String>> uploadProfilePhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) UUID userId
    ) {
        byte[] compressed = imageProcessingService.recompress(
                readBytes(file), PROFILE_PHOTO_MAX_DIMENSION, maxProfilePhotoBytes);

        String key = "profile/" + owner(userId) + "/" + UUID.randomUUID() + ".jpg";
        return created(storageService.upload(key, compressed, "image/jpeg").url());
    }

    /** Up to ten photos for a listing hosted by {@code hostUserId}; URLs come back in upload order. */
    @PostMapping("/listing-photos")
    public ResponseEntity<Map<String, List<String>>> uploadListingPhotos(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "hostUserId", required = false) UUID hostUserId
    ) {
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No files provided");
        }
        if (files.size() > MAX_LISTING_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_LISTING_PHOTOS + " photos per upload");
        }

        List<byte[]> raw = files.stream().map(AdminUploadController::readBytes).toList();
        List<byte[]> compressed = imageProcessingService.recompressBatch(
                raw, LISTING_PHOTO_MAX_DIMENSION, maxListingPhotosTotalBytes);

        String owner = owner(hostUserId);
        List<String> urls = compressed.stream()
                .map(bytes -> {
                    String key = "listings/" + owner + "/" + UUID.randomUUID() + ".jpg";
                    return storageService.upload(key, bytes, "image/jpeg").url();
                })
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("urls", urls));
    }

    /**
     * Branding artwork. {@code kind} is one of {@code logo}, {@code splash} or {@code qr}:
     * the splash banner is recompressed as a JPEG photo, the logo and the UPI QR code are kept
     * as lossless PNGs.
     */
    @PostMapping("/branding-image")
    public ResponseEntity<Map<String, String>> uploadBrandingImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") String kind
    ) {
        String normalized = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        byte[] raw = readBytes(file);

        return switch (normalized) {
            case "splash" -> {
                byte[] jpeg = imageProcessingService.recompress(raw, SPLASH_MAX_DIMENSION, SPLASH_MAX_BYTES);
                yield created(storageService.upload(brandingKey(normalized, "jpg"), jpeg, "image/jpeg").url());
            }
            case "logo", "qr" -> {
                byte[] png = imageProcessingService.toPng(raw, ARTWORK_MAX_DIMENSION);
                yield created(storageService.upload(brandingKey(normalized, "png"), png, "image/png").url());
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "kind must be one of: logo, splash, qr");
        };
    }

    private static String brandingKey(String kind, String extension) {
        return "branding/" + kind + "-" + UUID.randomUUID() + "." + extension;
    }

    private static String owner(UUID userId) {
        return userId != null ? userId.toString() : UNASSIGNED_OWNER;
    }

    private static ResponseEntity<Map<String, String>> created(String url) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }
}
