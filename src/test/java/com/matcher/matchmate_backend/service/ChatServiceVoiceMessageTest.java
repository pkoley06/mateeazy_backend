package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.controller.UploadController;
import com.matcher.matchmate_backend.dto.response.MessageResponse;
import com.matcher.matchmate_backend.event.MessageSentEvent;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Conversation;
import com.matcher.matchmate_backend.model.entity.Message;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Voice messages: one uploaded clip, a sane length, digits-only waveform, never editable. */
class ChatServiceVoiceMessageTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID THEM = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CLIP = "https://cdn.example/chat/me/clip.m4a";

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private ChatService service;
    private Conversation chat;

    private final AppUser me = AppUser.builder().id(ME).fullName("Me").build();
    private final AppUser them = AppUser.builder().id(THEM).fullName("Them").build();

    @BeforeEach
    void setUp() {
        UserBlockRepository blocks = mock(UserBlockRepository.class);
        service = new ChatService(conversations, messages, users, mock(ListingRepository.class), blocks,
                mock(PresenceRegistry.class), events, storage,
                mock(ChatMediaQuotaService.class), mock(MessageReactionRepository.class));
        chat = Conversation.builder().id(UUID.randomUUID()).userOne(me).userTwo(them).build();
        when(conversations.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(users.findById(ME)).thenReturn(Optional.of(me));
        when(users.findById(THEM)).thenReturn(Optional.of(them));
        when(storage.isManagedUrl(CLIP)).thenReturn(true);
        when(messages.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setSentAt(OffsetDateTime.now());
            return m;
        });
    }

    private MessageResponse send(List<String> urls, Integer durationMs, String waveform) {
        return service.sendMessage(chat.getId(), ME, "ignored caption", MessageType.AUDIO, urls, null,
                durationMs, waveform);
    }

    @Test
    void storesTheClipWithItsLengthAndBars() {
        MessageResponse sent = send(List.of(CLIP), 12_340, "0359a72x");

        assertThat(sent.getMessageType()).isEqualTo(MessageType.AUDIO);
        assertThat(sent.getContent()).isEmpty();
        assertThat(sent.getAttachmentUrls()).containsExactly(CLIP);
        assertThat(sent.getDurationMs()).isEqualTo(12_340);
        assertThat(sent.getWaveform()).isEqualTo("035972");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        MessageSentEvent sentEvent = (MessageSentEvent) event.getValue();
        assertThat(sentEvent.durationMs()).isEqualTo(12_340);
        assertThat(sentEvent.previewText()).isEqualTo("🎤 Voice message");
    }

    @Test
    void rejectsAClipNotUploadedThroughTheApp() {
        assertThatThrownBy(() -> send(List.of("https://elsewhere.example/a.m4a"), 5_000, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsAMissingOrOutOfRangeLength() {
        assertThatThrownBy(() -> send(List.of(CLIP), null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> send(List.of(CLIP), 100, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> send(List.of(CLIP), 6 * 60 * 1000, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsZeroOrSeveralClips() {
        assertThatThrownBy(() -> send(List.of(), 5_000, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> send(List.of(CLIP, CLIP), 5_000, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aVoiceMessageCannotBeEdited() {
        Message voice = Message.builder().id(UUID.randomUUID()).conversation(chat).sender(me).recipient(them)
                .content("").messageType(MessageType.AUDIO).attachmentUrls(List.of(CLIP)).build();
        when(messages.findById(voice.getId())).thenReturn(Optional.of(voice));

        assertThatThrownBy(() -> service.editMessage(voice.getId(), ME, "new words"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void onlyMp4ContainersPassTheUploadCheck() {
        byte[] m4a = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
        assertThat(UploadController.isMp4(m4a)).isTrue();
        assertThat(UploadController.isMp4(gif)).isFalse();
    }
}
