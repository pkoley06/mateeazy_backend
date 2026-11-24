package com.matcher.matchmate_backend.share;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.model.enums.listing.FurnishingStatus;
import com.matcher.matchmate_backend.model.enums.listing.ListingType;
import com.matcher.matchmate_backend.model.enums.listing.RoomType;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the public preview for a shared room or community post.
 *
 * <p>Deliberately minimal and public-safe: a headline, rent / area line and the cover photo.
 * Never the poster's name, the exact address or map position, contact details or comments.
 * Only items other signed-in users could see right now are previewed: an available listing /
 * active post whose owner is active and not suspended. Anything else reads as "no longer
 * available", without saying why.
 */
@Service
public class SharePreviewService {

    private static final int SUMMARY_MAX = 160;

    private final ListingRepository listingRepository;
    private final CommunityPostRepository communityPostRepository;

    public SharePreviewService(ListingRepository listingRepository, CommunityPostRepository communityPostRepository) {
        this.listingRepository = listingRepository;
        this.communityPostRepository = communityPostRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SharePreview> room(UUID id) {
        return listingRepository.findById(id)
                .filter(l -> Boolean.TRUE.equals(l.getIsAvailable()))
                .filter(l -> isVisible(l.getHostUser()))
                .map(SharePreviewService::toPreview);
    }

    @Transactional(readOnly = true)
    public Optional<SharePreview> post(UUID id) {
        return communityPostRepository.findById(id)
                .filter(p -> p.getStatus() == CommunityPostStatus.ACTIVE)
                .filter(p -> isVisible(p.getAuthor()))
                .map(SharePreviewService::toPreview);
    }

    static boolean isVisible(AppUser owner) {
        return owner != null
                && Boolean.TRUE.equals(owner.getIsActive())
                && !Boolean.TRUE.equals(owner.getIsSuspended())
                && (owner.getAccountStatus() == null || owner.getAccountStatus() == AccountStatus.ACTIVE);
    }

    static SharePreview toPreview(Listing listing) {
        String area = joinNonBlank(", ", listing.getLocalityName(), listing.getCity());
        String rent = rupees(listing.getRentAmount());
        boolean seeking = listing.getListingType() == ListingType.NEED_ROOM;

        String title;
        if (seeking) {
            title = "Looking for a room in " + orDefault(listing.getLocalityName(), "the area");
        } else if (listing.getTitle() != null && !listing.getTitle().isBlank()) {
            title = listing.getTitle().trim();
        } else {
            title = roomLabel(listing.getRoomType()) + " in " + orDefault(listing.getLocalityName(), "the city");
        }

        List<String> parts = new ArrayList<>();
        if (rent != null) parts.add(seeking ? "Budget " + rent + "/month" : rent + "/month");
        if (!seeking && listing.getRoomType() != null) parts.add(roomLabel(listing.getRoomType()));
        if (!seeking && listing.getFurnishingStatus() != null) parts.add(furnishingLabel(listing.getFurnishingStatus()));
        if (!area.isEmpty()) parts.add(area);

        String cover = listing.getPhotoUrls() == null || listing.getPhotoUrls().isEmpty()
                ? null
                : listing.getPhotoUrls().get(0);
        return new SharePreview(SharePreview.Kind.ROOM, listing.getId().toString(), title,
                String.join(" · ", parts), cover);
    }

    static SharePreview toPreview(CommunityPost post) {
        String content = orDefault(post.getContent(), "").replaceAll("\\s+", " ").trim();
        String title = post.getTitle() != null && !post.getTitle().isBlank()
                ? post.getTitle().trim()
                : truncate(content.isEmpty() ? "A post on MateEazy Community" : content, 70);
        String area = joinNonBlank(", ", post.getLocalityName(), post.getCity());
        String summary = truncate(content, SUMMARY_MAX);
        if (!area.isEmpty()) summary = summary.isEmpty() ? area : summary + " · " + area;

        String cover = post.getImageUrls() == null || post.getImageUrls().isEmpty()
                ? null
                : post.getImageUrls().get(0);
        return new SharePreview(SharePreview.Kind.POST, post.getId().toString(), title, summary, cover);
    }

    private static String rupees(Integer amount) {
        if (amount == null || amount <= 0) return null;
        return "₹" + NumberFormat.getIntegerInstance(Locale.ENGLISH).format(amount);
    }

    private static String roomLabel(RoomType type) {
        if (type == null) return "Room";
        return switch (type) {
            case PRIVATE -> "Private room";
            case SHARE -> "Shared room";
        };
    }

    private static String furnishingLabel(FurnishingStatus status) {
        return switch (status) {
            case FURNISHED -> "Furnished";
            case SEMI_FURNISHED -> "Semi-furnished";
            case UNFURNISHED -> "Unfurnished";
        };
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 1).trim() + "…";
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> kept = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank() && !kept.contains(v.trim())) kept.add(v.trim());
        }
        return String.join(separator, kept);
    }
}
