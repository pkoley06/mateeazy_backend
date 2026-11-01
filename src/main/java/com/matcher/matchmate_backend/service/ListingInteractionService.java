package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.request.ListingCommentCreateRequest;
import com.matcher.matchmate_backend.dto.response.ListingCommentEvent;
import com.matcher.matchmate_backend.dto.response.ListingCommentLikeStatusResponse;
import com.matcher.matchmate_backend.dto.response.ListingCommentResponse;
import com.matcher.matchmate_backend.dto.response.ListingLikeStatusResponse;
import com.matcher.matchmate_backend.dto.response.ListingResponse;
import com.matcher.matchmate_backend.event.ListingCommentChangedEvent;
import com.matcher.matchmate_backend.event.ListingCommentCreatedEvent;
import com.matcher.matchmate_backend.event.ListingLikedEvent;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.ListingComment;
import com.matcher.matchmate_backend.model.entity.ListingCommentLike;
import com.matcher.matchmate_backend.model.entity.ListingLike;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingCommentLikeRepository;
import com.matcher.matchmate_backend.repository.ListingCommentRepository;
import com.matcher.matchmate_backend.repository.ListingLikeRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Likes and comments on rooms. Writes publish application events that are relayed after
 * commit ({@code ListingInteractionEventListener}) to STOMP subscribers and, where the
 * host's preferences allow, to FCM.
 */
@Service
public class ListingInteractionService {

    private final ListingLikeRepository likeRepository;
    private final ListingCommentRepository commentRepository;
    private final ListingCommentLikeRepository commentLikeRepository;
    private final ListingRepository listingRepository;
    private final AppUserRepository userRepository;
    private final ListingService listingService;
    private final ApplicationEventPublisher eventPublisher;

    public ListingInteractionService(
            ListingLikeRepository likeRepository,
            ListingCommentRepository commentRepository,
            ListingCommentLikeRepository commentLikeRepository,
            ListingRepository listingRepository,
            AppUserRepository userRepository,
            ListingService listingService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.listingService = listingService;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------ room likes

    @Transactional
    public ListingLikeStatusResponse toggleLike(UUID userId, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        Optional<ListingLike> existing = likeRepository.findByUserIdAndListingId(userId, listingId);
        boolean isLiked;
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            isLiked = false;
        } else {
            likeRepository.save(ListingLike.builder()
                    .userId(userId)
                    .listingId(listingId)
                    .build());
            isLiked = true;
            AppUser host = listing.getHostUser();
            AppUser liker = userRepository.findById(userId).orElse(null);
            if (host != null && liker != null) {
                eventPublisher.publishEvent(new ListingLikedEvent(
                        listing.getId(), listing.getTitle(), listing.getLocalityName(),
                        host.getId(), liker.getId(), liker.getFullName()));
            }
        }

        long count = likeRepository.countByListingId(listingId);
        return ListingLikeStatusResponse.builder()
                .listingId(listingId)
                .isLiked(isLiked)
                .likesCount(count)
                .build();
    }

    public ListingLikeStatusResponse getLikeStatus(UUID userId, UUID listingId) {
        if (!listingRepository.existsById(listingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        }
        boolean isLiked = likeRepository.existsByUserIdAndListingId(userId, listingId);
        long count = likeRepository.countByListingId(listingId);
        return ListingLikeStatusResponse.builder()
                .listingId(listingId)
                .isLiked(isLiked)
                .likesCount(count)
                .build();
    }

    /** Transactional so {@code toResponse} can read the lazy host (open-in-view is off). */
    @Transactional
    public List<ListingResponse> getLikedListings(UUID userId) {
        List<Listing> listings = likeRepository.findLikedListingsByUserId(userId);
        return listings.stream().map(listingService::toResponse).toList();
    }

    // ------------------------------------------------------------------ comments

    /** Full thread, oldest first, with like counts and the viewer's own likes resolved in two queries. */
    @Transactional
    public List<ListingCommentResponse> getComments(UUID viewerUserId, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        UUID hostId = listing.getHostUser() != null ? listing.getHostUser().getId() : null;

        List<ListingComment> comments = commentRepository.findThread(listingId);
        if (comments.isEmpty()) return List.of();

        List<UUID> ids = comments.stream().map(ListingComment::getId).toList();
        Map<UUID, Long> likeCounts = new HashMap<>();
        for (Object[] row : commentLikeRepository.countByCommentIds(ids)) {
            likeCounts.put((UUID) row[0], (Long) row[1]);
        }
        Set<UUID> likedByViewer = viewerUserId != null
                ? commentLikeRepository.findLikedCommentIds(viewerUserId, ids)
                : Collections.emptySet();

        return comments.stream()
                .map(c -> toCommentResponse(c, hostId, likeCounts.getOrDefault(c.getId(), 0L), likedByViewer.contains(c.getId())))
                .toList();
    }

    @Transactional
    public ListingCommentResponse addComment(UUID userId, UUID listingId, ListingCommentCreateRequest request) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        AppUser author = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        ListingComment saved = commentRepository.save(ListingComment.builder()
                .listing(listing)
                .user(author)
                .content(request.getContent().trim())
                .build());

        AppUser host = listing.getHostUser();
        UUID hostId = host != null ? host.getId() : null;
        ListingCommentResponse response = toCommentResponse(saved, hostId, 0L, false);
        long commentsCount = commentRepository.countByListingId(listingId);

        eventPublisher.publishEvent(new ListingCommentChangedEvent(ListingCommentEvent.builder()
                .type(ListingCommentEvent.Type.COMMENT_CREATED)
                .listingId(listingId)
                .commentId(saved.getId())
                .comment(forBroadcast(response))
                .commentsCount(commentsCount)
                .build()));
        if (hostId != null) {
            eventPublisher.publishEvent(new ListingCommentCreatedEvent(
                    listingId, listing.getTitle(), listing.getLocalityName(),
                    hostId, author.getId(), author.getFullName(), saved.getContent()));
        }
        return response;
    }

    /** Author or room host may delete. */
    @Transactional
    public void deleteComment(UUID userId, UUID commentId) {
        ListingComment comment = commentRepository.findWithRelations(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        boolean isAuthor = comment.getUser().getId().equals(userId);
        boolean isHost = comment.getListing().getHostUser().getId().equals(userId);
        if (!isAuthor && !isHost) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete this comment");
        }
        removeComment(comment);
    }

    /** Admin moderation: no ownership check. */
    @Transactional
    public void deleteCommentAsModerator(UUID commentId) {
        ListingComment comment = commentRepository.findWithRelations(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        removeComment(comment);
    }

    private void removeComment(ListingComment comment) {
        UUID listingId = comment.getListing().getId();
        UUID commentId = comment.getId();
        commentRepository.delete(comment);
        commentRepository.flush();
        long remaining = commentRepository.countByListingId(listingId);

        eventPublisher.publishEvent(new ListingCommentChangedEvent(ListingCommentEvent.builder()
                .type(ListingCommentEvent.Type.COMMENT_DELETED)
                .listingId(listingId)
                .commentId(commentId)
                .commentsCount(remaining)
                .build()));
    }

    // ------------------------------------------------------------------ comment likes

    @Transactional
    public ListingCommentLikeStatusResponse toggleCommentLike(UUID userId, UUID commentId) {
        ListingComment comment = commentRepository.findWithRelations(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        Optional<ListingCommentLike> existing = commentLikeRepository.findByCommentIdAndUserId(commentId, userId);
        boolean liked;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            liked = false;
        } else {
            commentLikeRepository.save(ListingCommentLike.builder().commentId(commentId).userId(userId).build());
            liked = true;
        }
        commentLikeRepository.flush();
        long likesCount = commentLikeRepository.countByCommentId(commentId);

        Listing listing = comment.getListing();
        UUID hostId = listing.getHostUser() != null ? listing.getHostUser().getId() : null;
        eventPublisher.publishEvent(new ListingCommentChangedEvent(ListingCommentEvent.builder()
                .type(ListingCommentEvent.Type.COMMENT_LIKE_UPDATED)
                .listingId(listing.getId())
                .commentId(commentId)
                .comment(forBroadcast(toCommentResponse(comment, hostId, likesCount, false)))
                .commentsCount(commentRepository.countByListingId(listing.getId()))
                .build()));

        return ListingCommentLikeStatusResponse.builder()
                .commentId(commentId)
                .likedByMe(liked)
                .likesCount(likesCount)
                .build();
    }

    // ------------------------------------------------------------------ mapping

    public ListingCommentResponse toCommentResponse(ListingComment comment, UUID hostUserId, long likesCount, boolean likedByMe) {
        AppUser author = comment.getUser();
        return ListingCommentResponse.builder()
                .id(comment.getId())
                .listingId(comment.getListing().getId())
                .userId(author.getId())
                .authorName(author.getFullName())
                .authorAvatarUrl(author.getAvatarUrl())
                .authorVerified(Boolean.TRUE.equals(author.getOnboardingCompleted()))
                .authorIsHost(hostUserId != null && hostUserId.equals(author.getId()))
                .content(comment.getContent())
                .likesCount(likesCount)
                .likedByMe(likedByMe)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    /** Broadcast frames reach every subscriber, so the per-viewer flag is cleared. */
    private static ListingCommentResponse forBroadcast(ListingCommentResponse response) {
        return ListingCommentResponse.builder()
                .id(response.getId())
                .listingId(response.getListingId())
                .userId(response.getUserId())
                .authorName(response.getAuthorName())
                .authorAvatarUrl(response.getAuthorAvatarUrl())
                .authorVerified(response.isAuthorVerified())
                .authorIsHost(response.isAuthorIsHost())
                .content(response.getContent())
                .likesCount(response.getLikesCount())
                .likedByMe(null)
                .createdAt(response.getCreatedAt())
                .build();
    }
}
