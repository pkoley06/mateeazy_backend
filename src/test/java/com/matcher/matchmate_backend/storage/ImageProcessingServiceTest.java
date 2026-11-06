package com.matcher.matchmate_backend.storage;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test — no Spring context needed; the service is stateless. */
class ImageProcessingServiceTest {

    private final ImageProcessingService service = new ImageProcessingService();

    @Test
    void recompressFitsUnderByteBudget() throws IOException {
        byte[] source = largeNoisyJpeg(2000, 1500);
        long budget = 200 * 1024L;

        byte[] result = service.recompress(source, 1024, budget);

        assertThat((long) result.length).isLessThanOrEqualTo(budget);
        assertThat(service.decodeOrReject(result)).isNotNull();
    }

    @Test
    void recompressBatchFitsCombinedSizeUnderBudget() throws IOException {
        List<byte[]> source = List.of(
                largeNoisyJpeg(1800, 1200),
                largeNoisyJpeg(1800, 1200),
                largeNoisyJpeg(1800, 1200)
        );
        long totalBudget = 2L * 1024 * 1024;

        List<byte[]> result = service.recompressBatch(source, 1600, totalBudget);

        assertThat(result).hasSize(3);
        long total = result.stream().mapToLong(b -> b.length).sum();
        assertThat(total).isLessThanOrEqualTo(totalBudget);
    }

    @Test
    void toPngKeepsTransparencyAndSizeOfSmallArtwork() throws IOException {
        BufferedImage logo = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        logo.setRGB(10, 10, 0xFFFF0000); // one opaque red pixel, the rest fully transparent

        BufferedImage result = service.decodeOrReject(service.toPng(png(logo), 1024));

        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(200);
        assertThat(result.getRGB(10, 10)).isEqualTo(0xFFFF0000);
        assertThat(result.getRGB(0, 0) >>> 24).isZero(); // still transparent
    }

    @Test
    void toPngScalesDownOversizedArtwork() throws IOException {
        BufferedImage qr = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);

        BufferedImage result = service.decodeOrReject(service.toPng(png(qr), 1024));

        assertThat(result.getWidth()).isEqualTo(1024);
        assertThat(result.getHeight()).isEqualTo(512);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void rejectsDataThatIsNotAnImage() {
        byte[] garbage = "not an image".getBytes();

        assertThatThrownBy(() -> service.decodeOrReject(garbage))
                .hasMessageContaining("not a valid image");
    }

    /** A real-ish, incompressible-looking JPEG so quality steps actually move the size needle. */
    private static byte[] largeNoisyJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(42);
        for (int x = 0; x < width; x += 3) {
            for (int y = 0; y < height; y += 3) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
