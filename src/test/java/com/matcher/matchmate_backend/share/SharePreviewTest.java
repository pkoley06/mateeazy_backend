package com.matcher.matchmate_backend.share;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the shared-link preview: what is public, and how it is rendered. */
class SharePreviewTest {

    private static final UUID ID = UUID.fromString("0f8c5c2e-6a51-4f0e-9a39-2a7d1e3c4b5a");
    private final SharePageRenderer renderer = new SharePageRenderer("https://mateeazy.in/");

    private static AppUser host(boolean active, boolean suspended, AccountStatus status) {
        return AppUser.builder().fullName("Priya Sharma").isActive(active).isSuspended(suspended)
                .accountStatus(status).build();
    }

    private static Listing.ListingBuilder room() {
        return Listing.builder()
                .id(ID)
                .listingType(ListingType.HAVE_ROOM)
                .title("Sunny room near Metro")
                .rentAmount(12000)
                .roomType(RoomType.PRIVATE)
                .furnishingStatus(FurnishingStatus.FURNISHED)
                .localityName("HSR Layout")
                .city("Bengaluru")
                .description("Call me on 98xxxxxx10, flat 4B")
                .photoUrls(List.of("https://cdn.example/a.jpg", "https://cdn.example/b.jpg"));
    }

    @Test
    void roomPreviewShowsHeadlineRentAreaAndCoverButNoPersonalDetails() {
        SharePreview p = SharePreviewService.toPreview(room().build());

        assertThat(p.title()).isEqualTo("Sunny room near Metro");
        assertThat(p.summary()).isEqualTo("₹12,000/month · Private room · Furnished · HSR Layout, Bengaluru");
        assertThat(p.imageUrl()).isEqualTo("https://cdn.example/a.jpg");
        String html = renderer.render(p);
        assertThat(html).doesNotContain("Priya").doesNotContain("98xxxxxx10").doesNotContain("4B");
    }

    @Test
    void seekerListingNeverUsesTheirTitle() {
        SharePreview p = SharePreviewService.toPreview(
                room().listingType(ListingType.NEED_ROOM).title("Priya needs a room").rentAmount(9000).build());

        assertThat(p.title()).isEqualTo("Looking for a room in HSR Layout");
        assertThat(p.summary()).isEqualTo("Budget ₹9,000/month · HSR Layout, Bengaluru");
    }

    @Test
    void onlyActiveUnsuspendedOwnersArePreviewed() {
        assertThat(SharePreviewService.isVisible(host(true, false, AccountStatus.ACTIVE))).isTrue();
        assertThat(SharePreviewService.isVisible(host(false, false, AccountStatus.ACTIVE))).isFalse();
        assertThat(SharePreviewService.isVisible(host(true, true, AccountStatus.ACTIVE))).isFalse();
        assertThat(SharePreviewService.isVisible(host(true, false, AccountStatus.DISABLED))).isFalse();
        assertThat(SharePreviewService.isVisible(host(true, false, AccountStatus.PENDING_DELETION))).isFalse();
        assertThat(SharePreviewService.isVisible(null)).isFalse();
    }

    @Test
    void postPreviewTruncatesContent() {
        CommunityPost post = CommunityPost.builder()
                .id(ID).title(null).content("x".repeat(300)).localityName("Salt Lake").city("Kolkata").build();

        SharePreview p = SharePreviewService.toPreview(post);

        assertThat(p.title()).hasSize(70).endsWith("…");
        assertThat(p.summary()).endsWith("… · Salt Lake, Kolkata");
        assertThat(p.imageUrl()).isNull();
    }

    @Test
    void pageCarriesOpenGraphTagsAndAppLinks() {
        String html = renderer.render(SharePreviewService.toPreview(room().build()));

        assertThat(html)
                .contains("<meta property=\"og:title\" content=\"Sunny room near Metro\">")
                .contains("<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">")
                .contains("<meta property=\"og:url\" content=\"https://mateeazy.in/room/" + ID + "\">")
                .contains("<meta name=\"robots\" content=\"noindex\">")
                .contains("intent://mateeazy.in/room/" + ID + "#Intent;scheme=https;package=com.mateeazy.app")
                .contains(SharePageRenderer.PLAY_URL);
    }

    @Test
    void userTextIsEscaped() {
        String html = renderer.render(SharePreviewService.toPreview(
                room().title("<script>alert(1)</script>\" onload=\"x").build()));

        assertThat(html).doesNotContain("<script>alert").contains("&lt;script&gt;alert(1)&lt;/script&gt;&quot;");
    }

    @Test
    void roomWithoutPhotosUsesTheSiteImage() {
        String html = renderer.render(SharePreviewService.toPreview(room().photoUrls(List.of()).build()));

        assertThat(html).contains("content=\"https://mateeazy.in/assets/img/og-image.jpg\"");
    }

    @Test
    void missingPageSaysUnavailable() {
        assertThat(renderer.renderMissing(SharePreview.Kind.ROOM)).contains("This room is no longer available");
        assertThat(renderer.renderMissing(SharePreview.Kind.POST)).contains("This post is no longer available");
    }
}
