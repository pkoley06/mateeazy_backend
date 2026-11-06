package com.matcher.matchmate_backend.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Re-encodes uploaded photos down to a byte budget without destroying visibility: steps JPEG
 * quality down first, then (only if still over budget) the longest side, re-measuring the
 * actual encoded size each time. This is the server-side enforcement backing the client-side
 * compression the Flutter app already does — belt and suspenders, since a request could come
 * from anywhere, not just the app.
 */
@Service
public class ImageProcessingService {

    private static final double[] QUALITY_STEPS = {0.85, 0.7, 0.55, 0.4};

    /** Decodes {@code data} as an image, rejecting anything that isn't one. */
    public BufferedImage decodeOrReject(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid image");
            }
            return image;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is not a valid image");
        }
    }

    /**
     * Recompresses {@code data} to a JPEG no larger than {@code maxBytes}, trying progressively
     * lower quality at {@code maxLongestSide}, then progressively smaller dimensions at the
     * lowest quality. Returns the smallest result achieved even if it couldn't quite fit under
     * budget (callers decide whether that's acceptable).
     */
    public byte[] recompress(byte[] data, int maxLongestSide, long maxBytes) {
        BufferedImage image = decodeOrReject(data);

        byte[] best = null;
        for (double quality : QUALITY_STEPS) {
            byte[] attempt = encodeJpeg(image, maxLongestSide, quality);
            if (best == null || attempt.length < best.length) best = attempt;
            if (attempt.length <= maxBytes) return attempt;
        }

        int dimension = maxLongestSide;
        for (int i = 0; i < 3; i++) {
            dimension = dimension / 2;
            if (dimension < 200) break;
            byte[] attempt = encodeJpeg(image, dimension, QUALITY_STEPS[QUALITY_STEPS.length - 1]);
            if (attempt.length < best.length) best = attempt;
            if (attempt.length <= maxBytes) return attempt;
        }

        return best;
    }

    /**
     * Compresses a batch of images (e.g. a listing's photos) so their combined size fits
     * {@code maxTotalBytes}, preserving input order. First compresses each individually to a
     * reasonable per-image target, then — only if the batch is still over budget — re-runs the
     * whole batch at progressively lower quality until it fits or the quality floor is hit.
     */
    public List<byte[]> recompressBatch(List<byte[]> inputs, int maxLongestSide, long maxTotalBytes) {
        List<BufferedImage> images = new ArrayList<>(inputs.size());
        for (byte[] input : inputs) images.add(decodeOrReject(input));

        List<byte[]> result = new ArrayList<>(images.size());
        for (BufferedImage image : images) {
            result.add(encodeJpeg(image, maxLongestSide, QUALITY_STEPS[0]));
        }

        for (double quality : QUALITY_STEPS) {
            if (totalSize(result) <= maxTotalBytes) break;
            List<byte[]> attempt = new ArrayList<>(images.size());
            for (BufferedImage image : images) attempt.add(encodeJpeg(image, maxLongestSide, quality));
            if (totalSize(attempt) < totalSize(result)) result = attempt;
        }

        return result;
    }

    /**
     * Re-encodes {@code data} as a lossless PNG, scaled down only when its longest side exceeds
     * {@code maxLongestSide}. For artwork that JPEG would damage: a logo (transparency) or a
     * payment QR code (hard edges a scanner has to read).
     */
    public byte[] toPng(byte[] data, int maxLongestSide) {
        BufferedImage image = decodeOrReject(data);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (Math.max(image.getWidth(), image.getHeight()) > maxLongestSide) {
                Thumbnails.of(image)
                        .size(maxLongestSide, maxLongestSide)
                        .outputFormat("png")
                        .toOutputStream(out);
            } else {
                ImageIO.write(image, "png", out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process image");
        }
    }

    private static long totalSize(List<byte[]> items) {
        long total = 0;
        for (byte[] item : items) total += item.length;
        return total;
    }

    private static byte[] encodeJpeg(BufferedImage image, int maxLongestSide, double quality) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(image)
                    .size(maxLongestSide, maxLongestSide)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process image");
        }
    }
}
