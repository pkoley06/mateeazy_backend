package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ListingCommentRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docker-free unit tests for the two notification fan-outs: comment-thread participants and
 * the once-per-session "new seeker near your room" host alert. {@code @Async} methods run
 * synchronously here because the service is not a Spring proxy.
 */
class NotificationServiceFanOutTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final ListingRepository listingRepository = mock(ListingRepository.class);
    private final MatchingHeuristicEngine matchingEngine = mock(MatchingHeuristicEngine.class);
    private final NotificationPreferencesService preferencesService = mock(NotificationPreferencesService.class);
    private final UserNotificationService inboxService = mock(UserNotificationService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ListingCommentRepository commentRepository = mock(ListingCommentRepository.class);
    private final UserBlockRepository blockRepository = mock(UserBlockRepository.class);
    private final SeekerMatchAlertLedger ledger = mock(SeekerMatchAlertLedger.class);

    private NotificationService service;

    private final UUID listingId = UUID.randomUUID();
    private final AppUser host = user("Host");
    private final AppUser author = user("Author");
    private final AppUser p1 = user("P1");
    private final AppUser p2 = user("P2");

    @BeforeEach
    void setUp() {
        service = new NotificationService(userRepository, listingRepository, matchingEngine, preferencesService,
                inboxService, messagingTemplate, commentRepository, blockRepository, ledger);
        when(preferencesService.allows(any(), any())).thenReturn(true);
        when(blockRepository.findBlockedUserIdsByBlockerId(any())).thenReturn(Set.of());
        when(blockRepository.findBlockerIdsByBlockedId(any())).thenReturn(Set.of());
        when(inboxService.record(any(), anyString(), anyString(), anyString(), anyMap())).thenReturn(UUID.randomUUID());
    }

    @Test
    void commentNotifiesOtherParticipantsButNotAuthorOrHost() {
        when(commentRepository.findCommenterIds(listingId))
                .thenReturn(List.of(author.getId(), host.getId(), p1.getId(), p2.getId()));
        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            assertThat(ids).containsExactlyInAnyOrder(p1.getId(), p2.getId());
            return List.of(p1, p2);
        });

        service.notifyParticipantsOfNewComment(listingId, "Sunny room", "Baner",
                host.getId(), author.getId(), "Author", "Is it still available?");

        verify(inboxService).record(eq(p1.getId()), eq("LISTING_COMMENT_REPLY"), anyString(), anyString(), anyMap());
        verify(inboxService).record(eq(p2.getId()), eq("LISTING_COMMENT_REPLY"), anyString(), anyString(), anyMap());
        verify(inboxService, never()).record(eq(author.getId()), anyString(), anyString(), anyString(), anyMap());
        verify(inboxService, never()).record(eq(host.getId()), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void hostReplyReachesCommentersWithHostTitle() {
        when(commentRepository.findCommenterIds(listingId)).thenReturn(List.of(host.getId(), p1.getId()));
        when(userRepository.findAllById(any())).thenReturn(List.of(p1));

        service.notifyParticipantsOfNewComment(listingId, "Sunny room", "Baner",
                host.getId(), host.getId(), "Host", "Yes, still available");

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(inboxService).record(eq(p1.getId()), eq("LISTING_COMMENT_REPLY"), title.capture(), anyString(), anyMap());
        assertThat(title.getValue()).contains("Host Host replied on Sunny room");
    }

    @Test
    void mutedAndBlockedParticipantsAreSkipped() {
        when(commentRepository.findCommenterIds(listingId)).thenReturn(List.of(p1.getId(), p2.getId()));
        when(userRepository.findAllById(any())).thenReturn(List.of(p1, p2));
        when(blockRepository.findBlockerIdsByBlockedId(author.getId())).thenReturn(Set.of(p1.getId()));
        when(preferencesService.allows(eq(p2.getId()), any())).thenReturn(false);

        service.notifyParticipantsOfNewComment(listingId, "Sunny room", "Baner",
                host.getId(), author.getId(), "Author", "Hi");

        verify(inboxService, never()).record(any(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void hostWithTwoNearbyListingsIsAlertedOnceAboutASeeker() {
        AppUser seeker = user("Seeker");
        seeker.setTargetSearchLocation(new GeometryFactory().createPoint(new Coordinate(73.8, 18.5)));
        seeker.setRoomFinderSince(OffsetDateTime.now());
        Listing first = Listing.builder().id(UUID.randomUUID()).hostUser(host).build();
        Listing second = Listing.builder().id(UUID.randomUUID()).hostUser(host).build();
        when(listingRepository.findActiveListingsWithinRadius(any(), anyDouble(), any(), any()))
                .thenReturn(List.of(first, second));
        when(matchingEngine.evaluate(any(), any())).thenReturn(MatchingHeuristicEngine.MatchResult.incomplete());
        // The ledger grants the first claim for this (host, seeker) pair and refuses every later one.
        when(ledger.claim(host.getId(), seeker.getId(), seeker.getRoomFinderSince())).thenReturn(true, false);

        service.notifyHostsForNewSeeker(seeker);
        service.notifyHostsForNewSeeker(seeker); // e.g. the alert-area save right after the room-seeker toggle

        verify(inboxService, times(1)).record(eq(host.getId()), eq("ROOM_SEEKER_MATCH"), anyString(), anyString(), anyMap());
    }

    private static AppUser user(String name) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setFullName(name);
        return u;
    }
}
