package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.ConversationSummaryResponse;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Conversation;
import com.matcher.matchmate_backend.model.entity.Message;
import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import com.matcher.matchmate_backend.model.enums.chat.MessageType;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ConversationRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.MessageReactionRepository;
import com.matcher.matchmate_backend.repository.MessageRepository;
import com.matcher.matchmate_backend.repository.UserBlockRepository;
import com.matcher.matchmate_backend.storage.StorageService;
import com.matcher.matchmate_backend.websocket.PresenceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Chat list rules: a chat nobody has written in stays out of the (current app's) list, and
 * "Delete chat" hides it for one participant only until a newer message arrives.
 */
class ChatServiceDeleteChatTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID THEM = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private ChatService service;

    private final AppUser me = AppUser.builder().id(ME).fullName("Me").build();
    private final AppUser them = AppUser.builder().id(THEM).fullName("Them").build();

    @BeforeEach
    void setUp() {
        service = new ChatService(conversations, messages, mock(AppUserRepository.class), listings, blocks,
                mock(PresenceRegistry.class), mock(ApplicationEventPublisher.class), mock(StorageService.class),
                mock(ChatMediaQuotaService.class), mock(MessageReactionRepository.class));
        when(blocks.findBlockedUserIdsByBlockerId(any())).thenReturn(java.util.Set.of());
        when(blocks.findBlockerIdsByBlockedId(any())).thenReturn(java.util.Set.of());
        when(listings.findFirstByHostUserIdAndIsAvailableTrueOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
    }

    private Conversation chat() {
        Conversation c = Conversation.builder().id(UUID.randomUUID()).userOne(me).userTwo(them).build();
        when(conversations.findById(c.getId())).thenReturn(Optional.of(c));
        when(conversations.findAllInvolvingUser(any())).thenReturn(List.of(c));
        return c;
    }

    private Message message(Conversation c, OffsetDateTime sentAt) {
        return Message.builder().id(UUID.randomUUID()).conversation(c).sender(them).recipient(me)
                .content("hi").messageType(MessageType.TEXT).status(MessageStatus.SENT)
                .attachmentUrls(List.of()).sentAt(sentAt).build();
    }

    private void history(Conversation c, Message... all) {
        when(messages.findByConversationIdOrderBySentAtDesc(eq(c.getId()), any(Pageable.class)))
                .thenAnswer(inv -> page(List.of(all)));
        when(messages.findByConversationIdAndSentAtAfterOrderBySentAtDesc(eq(c.getId()), any(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    OffsetDateTime after = inv.getArgument(1);
                    return page(List.of(all).stream().filter(m -> m.getSentAt().isAfter(after)).toList());
                });
    }

    private static Page<Message> page(List<Message> content) {
        return new PageImpl<>(content, PageRequest.of(0, 30), content.size());
    }

    private static List<UUID> ids(List<ConversationSummaryResponse> list) {
        return list.stream().map(ConversationSummaryResponse::getId).toList();
    }

    @Test
    void chatWithNoMessagesIsHiddenFromTheCurrentAppButKeptForOlderOnes() {
        Conversation c = chat();
        history(c);

        assertThat(service.listConversations(ME, true)).isEmpty();
        assertThat(service.listConversations(THEM, true)).isEmpty();
        // Older app versions find a just-created chat in the list to open its thread.
        assertThat(ids(service.listConversations(ME, false))).containsExactly(c.getId());
    }

    @Test
    void emptyChatCanStillBeOpenedDirectly() {
        Conversation c = chat();
        history(c);

        assertThat(service.getConversation(c.getId(), ME).getId()).isEqualTo(c.getId());
        assertThatThrownBy(() -> service.getConversation(c.getId(), STRANGER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deletingHidesTheChatForMeOnly() {
        Conversation c = chat();
        c.setUnreadCountUserOne(3);
        history(c, message(c, OffsetDateTime.now().minusHours(1)));

        service.deleteConversationForUser(c.getId(), ME);

        assertThat(c.getClearedAtUserOne()).isNotNull();
        assertThat(c.getClearedAtUserTwo()).isNull();
        assertThat(c.getUnreadCountUserOne()).isZero();
        assertThat(service.listConversations(ME, true)).isEmpty();
        assertThat(service.listConversations(ME, false)).isEmpty();
        assertThat(service.getMessages(c.getId(), ME, PageRequest.of(0, 30)).getContent()).isEmpty();
        // The other side keeps the chat and its whole history.
        assertThat(ids(service.listConversations(THEM, true))).containsExactly(c.getId());
        assertThat(service.getMessages(c.getId(), THEM, PageRequest.of(0, 30)).getContent()).hasSize(1);
    }

    @Test
    void aNewMessageBringsTheChatBackWithOnlyWhatCameAfter() {
        Conversation c = chat();
        Message old = message(c, OffsetDateTime.now().minusHours(2));
        c.setClearedAtUserOne(OffsetDateTime.now().minusHours(1));
        Message fresh = message(c, OffsetDateTime.now());
        history(c, fresh, old);

        assertThat(ids(service.listConversations(ME, true))).containsExactly(c.getId());
        assertThat(service.getMessages(c.getId(), ME, PageRequest.of(0, 30)).getContent())
                .extracting(r -> r.getId()).containsExactly(fresh.getId());
    }

    @Test
    void onlyParticipantsCanDelete() {
        Conversation c = chat();

        assertThatThrownBy(() -> service.deleteConversationForUser(c.getId(), STRANGER))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(c.getClearedAtUserOne()).isNull();
        assertThat(c.getClearedAtUserTwo()).isNull();
    }
}
