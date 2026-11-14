package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.CommunityCommentCreateRequest;
import com.matcher.matchmate_backend.dto.request.CommunityPostCreateRequest;
import com.matcher.matchmate_backend.dto.response.CommunityCommentResponse;
import com.matcher.matchmate_backend.dto.response.CommunityLikeToggleResponse;
import com.matcher.matchmate_backend.dto.response.CommunityPostResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.CommunityComment;
import com.matcher.matchmate_backend.model.entity.CommunityPost;
import com.matcher.matchmate_backend.model.entity.CommunityPostLike;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostCategory;
import com.matcher.matchmate_backend.model.enums.community.CommunityPostStatus;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.CommunityCommentRepository;
import com.matcher.matchmate_backend.repository.CommunityPostLikeRepository;
import com.matcher.matchmate_backend.repository.CommunityPostRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommunityPostService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final AppUserRepository appUserRepository;
    private final ListingRepository listingRepository;

    public CommunityPostService(
            CommunityPostRepository communityPostRepository,
            CommunityPostLikeRepository communityPostLikeRepository,
            CommunityCommentRepository communityCommentRepository,
            AppUserRepository appUserRepository,
            ListingRepository listingRepository
    ) {
        this.communityPostRepository = communityPostRepository;
        this.communityPostLikeRepository = communityPostLikeRepository;
        this.communityCommentRepository = communityCommentRepository;
        this.appUserRepository = appUserRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostResponse> getFeed(
            UUID currentUserId,
            Double latitude,
            Double longitude,
            Double radiusKm,
            CommunityPostCategory category,
            Pageable pageable
    ) {
        Point userLocation = null;
        double radiusMeters = 50000; // default 50km
        if (latitude != null && longitude != null) {
            userLocation = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
            if (radiusKm != null && radiusKm > 0) {
                radiusMeters = radiusKm * 1000;
            }
        }

        String categoryFilter = (category != null && category != CommunityPostCategory.ALL) ? category.name() : null;

        Page<CommunityPost> postsPage = communityPostRepository.findPostsWithinRadius(
                userLocation,
                radiusMeters,
                categoryFilter,
                pageable
        );

        List<UUID> postIds = postsPage.getContent().stream().map(CommunityPost::getId).toList();
        Set<UUID> likedPostIds = (currentUserId != null && !postIds.isEmpty())
                ? communityPostLikeRepository.findByUserIdAndPostIdIn(currentUserId, postIds).stream()
                    .map(CommunityPostLike::getPostId).collect(Collectors.toSet())
                : Collections.emptySet();

        final Point finalUserLocation = userLocation;
        return postsPage.map(post -> toPostResponse(post, likedPostIds.contains(post.getId()), finalUserLocation));
    }

    @Transactional(readOnly = true)
    public CommunityPostResponse getPostById(UUID postId, UUID currentUserId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found: " + postId));

        boolean isLiked = currentUserId != null && communityPostLikeRepository.existsByPostIdAndUserId(postId, currentUserId);
        return toPostResponse(post, isLiked, null);
    }

    @Transactional
    public CommunityPostResponse createPost(UUID authorId, CommunityPostCreateRequest request) {
        AppUser author = appUserRepository.findById(authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + authorId));

        if (Boolean.TRUE.equals(author.getIsSuspended())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suspended users cannot create community posts");
        }

        Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        Listing taggedListing = null;
        if (request.getTaggedListingId() != null) {
            taggedListing = listingRepository.findById(request.getTaggedListingId()).orElse(null);
        }

        CommunityPost post = CommunityPost.builder()
                .author(author)
                .taggedListing(taggedListing)
                .category(request.getCategory())
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .priceAmount(request.getCategory() == CommunityPostCategory.MARKETPLACE ? request.getPriceAmount() : null)
                .location(location)
                .localityName(request.getLocalityName().trim())
                .city(request.getCity().trim())
                .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : new ArrayList<>())
                .likesCount(0)
                .commentsCount(0)
                .isPinned(false)
                .status(CommunityPostStatus.ACTIVE)
                .build();

        CommunityPost saved = communityPostRepository.save(post);
        return toPostResponse(saved, false, location);
    }

    @Transactional
    public void deletePost(UUID postId, UUID currentUserId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found: " + postId));

        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own posts");
        }

        communityPostRepository.delete(post);
    }

    @Transactional
    public CommunityLikeToggleResponse toggleLike(UUID postId, UUID currentUserId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found: " + postId));

        boolean alreadyLiked = communityPostLikeRepository.existsByPostIdAndUserId(postId, currentUserId);
        if (alreadyLiked) {
            communityPostLikeRepository.deleteByPostIdAndUserId(postId, currentUserId);
            communityPostRepository.updateLikesCount(postId, -1);
            int newCount = Math.max(0, post.getLikesCount() - 1);
            return CommunityLikeToggleResponse.builder()
                    .postId(postId)
                    .liked(false)
                    .likesCount(newCount)
                    .build();
        } else {
            CommunityPostLike like = CommunityPostLike.builder()
                    .postId(postId)
                    .userId(currentUserId)
                    .build();
            communityPostLikeRepository.save(like);
            communityPostRepository.updateLikesCount(postId, 1);
            int newCount = post.getLikesCount() + 1;
            return CommunityLikeToggleResponse.builder()
                    .postId(postId)
                    .liked(true)
                    .likesCount(newCount)
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public Page<CommunityCommentResponse> getComments(UUID postId, Pageable pageable) {
        if (!communityPostRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found: " + postId);
        }

        return communityCommentRepository.findByPostIdOrderByCreatedAtAsc(postId, pageable)
                .map(this::toCommentResponse);
    }

    @Transactional
    public CommunityCommentResponse addComment(UUID postId, UUID currentUserId, CommunityCommentCreateRequest request) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found: " + postId));

        AppUser user = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + currentUserId));

        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suspended users cannot comment");
        }

        CommunityComment comment = CommunityComment.builder()
                .post(post)
                .user(user)
                .content(request.getContent().trim())
                .build();

        CommunityComment saved = communityCommentRepository.save(comment);
        communityPostRepository.updateCommentsCount(postId, 1);

        return toCommentResponse(saved);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID currentUserId) {
        CommunityComment comment = communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found: " + commentId));

        if (!comment.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comments");
        }

        UUID postId = comment.getPost().getId();
        communityCommentRepository.delete(comment);
        communityPostRepository.updateCommentsCount(postId, -1);
    }

    private CommunityPostResponse toPostResponse(CommunityPost post, boolean isLikedByMe, Point fromLocation) {
        Point loc = post.getLocation();
        Double lat = loc != null ? loc.getY() : null;
        Double lng = loc != null ? loc.getX() : null;

        Double distanceKm = null;
        if (fromLocation != null && loc != null) {
            distanceKm = calculateDistanceKm(fromLocation.getY(), fromLocation.getX(), loc.getY(), loc.getX());
        }

        AppUser author = post.getAuthor();

        CommunityPostResponse.CommunityPostResponseBuilder builder = CommunityPostResponse.builder()
                .id(post.getId())
                .category(post.getCategory())
                .title(post.getTitle())
                .content(post.getContent())
                .priceAmount(post.getPriceAmount())
                .latitude(lat)
                .longitude(lng)
                .localityName(post.getLocalityName())
                .city(post.getCity())
                .distanceKm(distanceKm)
                .imageUrls(post.getImageUrls())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .isLikedByMe(isLikedByMe)
                .isPinned(post.getIsPinned())
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .authorId(author.getId())
                .authorName(author.getFullName())
                .authorAvatarUrl(author.getAvatarUrl())
                .authorIsVerified(Boolean.TRUE.equals(author.getIsIdentityVerified()))
                .authorOccupation(author.getOccupationTitle());

        if (post.getTaggedListing() != null) {
            Listing l = post.getTaggedListing();
            builder.taggedListingId(l.getId())
                   .taggedListingTitle(l.getTitle())
                   .taggedListingRent(l.getRentAmount())
                   .taggedListingCoverUrl(l.getPhotoUrls() != null && !l.getPhotoUrls().isEmpty() ? l.getPhotoUrls().get(0) : null);
        }

        return builder.build();
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment) {
        AppUser author = comment.getUser();
        return CommunityCommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .authorId(author.getId())
                .authorName(author.getFullName())
                .authorAvatarUrl(author.getAvatarUrl())
                .authorIsVerified(Boolean.TRUE.equals(author.getIsIdentityVerified()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private static Double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double dist = EARTH_RADIUS_KM * c;
        return Math.round(dist * 10.0) / 10.0;
    }
}
