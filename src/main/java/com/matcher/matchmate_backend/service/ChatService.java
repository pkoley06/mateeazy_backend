package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.ConversationListingSummary;
import com.matcher.matchmate_backend.dto.response.ConversationParticipantSummary;
import com.matcher.matchmate_backend.dto.response.ConversationSummaryResponse;
import com.matcher.matchmate_backend.dto.response.LastMessageSummary;
import com.matcher.matchmate_backend.dto.response.MessageResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.event.MessageSentEvent;
import com.matcher.matchmate_backend.event.MessagesDeliveredEvent;
import com.matcher.matchmate_backend.event.MessagesReadEvent;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Conversation;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.Message;
import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.repository.ConversationRepository;
import com.matcher.matchmate_backend.repository.ListingRepository;
import com.matcher.matchmate_backend.repository.MessageRepository;
import com.matcher.matchmate_backend.websocket.PresenceRegistry;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1-to-1 messaging. Writes publish application events that are relayed after commit
 * ({@code ChatEventListener}) to STOMP subscribers and, where the recipient has no live
 * presence session and their preferences allow it, to FCM.
 *
 * <p>Depends only on {@link PresenceRegistry} (never {@link PresenceService}) so that
 * presence -> chat -> presence never forms a circular bean graph; {@code PresenceService}
 * is the one that depends on this class (for the on-reconnect delivery sweep).
 */
@Service
public class ChatService {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AppUserRepository userRepository;
    private final ListingRepository listingRepository;
    private final PresenceRegistry presenceRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public ChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            PresenceRegistry presenceRegistry,
            ApplicationEventPublisher eventPublisher
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.presenceRegistry = presenceRegistry;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------ conversations

    @Transactional
    public ConversationSummaryResponse getOrCreateConversation(UUID requesterId, UUID otherUserId, UUID listingId) {
        if (requesterId.equals(otherUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start a conversation with yourself");
        }
        boolean requesterFirst = compareUuids(requesterId, otherUserId) < 0;
        UUID userOneId = requesterFirst ? requesterId : otherUserId;
        UUID userTwoId = requesterFirst ? otherUserId : requesterId;

        Conversation conversation = conversationRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                .orElseGet(() -> createConversation(userOneId, userTwoId, listingId));

        return toSummary(conversation, requesterId);
    }

    private Conversation createConversation(UUID userOneId, UUID userTwoId, UUID listingId) {
        AppUser userOne = userRepository.findById(userOneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        AppUser userTwo = userRepository.findById(userTwoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // Best-effort: an unresolvable listing id just means no listing context, not an error.
        Listing listing = listingId != null ? listingRepository.findById(listingId).orElse(null) : null;

        Conversation conversation = Conversation.builder()
                .userOne(userOne)
                .userTwo(userTwo)
                .listing(listing)
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional
    public List<ConversationSummaryResponse> listConversations(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllInvolvingUser(userId);
        return conversations.stream().map(c -> toSummary(c, userId)).toList();
    }

    // ------------------------------------------------------------------ messages

    @Transactional
    public PageResponse<MessageResponse> getMessages(UUID conversationId, UUID requesterId, Pageable pageable) {
        Conversation conversation = loadConversation(conversationId);
        requireParticipant(conversation, requesterId);
        Page<Message> page = messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);
        return PageResponse.of(page, this::toMessageResponse);
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, String content) {
        String trimmed = validateContent(content);
        Conversation conversation = loadConversation(conversationId);
        UUID recipientId = otherParticipantId(conversation, senderId);

        AppUser sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        AppUser recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean recipientOnline = presenceRegistry.isOnline(recipientId);
        OffsetDateTime now = OffsetDateTime.now();

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .recipient(recipient)
                .content(trimmed)
                .status(recipientOnline ? MessageStatus.DELIVERED : MessageStatus.SENT)
                .deliveredAt(recipientOnline ? now : null)
                .build();
        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        incrementUnread(conversation, recipientId);
        conversationRepository.save(conversation);

        eventPublisher.publishEvent(new MessageSentEvent(
                conversation.getId(), saved.getId(), senderId, recipientId,
                trimmed, saved.getSentAt(), saved.getStatus()));

        return toMessageResponse(saved);
    }

    @Transactional
    public void markRead(UUID conversationId, UUID readerId, UUID lastReadMessageId) {
        Conversation conversation = loadConversation(conversationId);
        requireParticipant(conversation, readerId);

        List<Message> unread = messageRepository.findByConversationIdAndRecipientIdAndStatusNot(
                conversationId, readerId, MessageStatus.READ);

        zeroUnread(conversation, readerId);
        conversationRepository.save(conversation);

        if (unread.isEmpty()) {
            return;
        }

        OffsetDateTime cutoff = lastReadMessageId != null
                ? messageRepository.findById(lastReadMessageId).map(Message::getSentAt).orElse(null)
                : null;

        List<Message> toMark = cutoff == null
                ? unread
                : unread.stream().filter(m -> !m.getSentAt().isAfter(cutoff)).toList();
        if (toMark.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<UUID, List<UUID>> messageIdsBySender = new HashMap<>();
        for (Message m : toMark) {
            m.setStatus(MessageStatus.READ);
            m.setReadAt(now);
            messageIdsBySender.computeIfAbsent(m.getSender().getId(), k -> new ArrayList<>()).add(m.getId());
        }
        messageRepository.saveAll(toMark);

        for (Map.Entry<UUID, List<UUID>> entry : messageIdsBySender.entrySet()) {
            eventPublisher.publishEvent(new MessagesReadEvent(conversationId, entry.getKey(), entry.getValue(), now));
        }
    }

    /** Called by {@code PresenceService.markOnline} once this user has a live session again. */
    @Transactional
    public void sweepDeliveryOnConnect(UUID userId) {
        List<Message> pending = messageRepository.findByRecipientIdAndStatus(userId, MessageStatus.SENT);
        if (pending.isEmpty()) return;

        OffsetDateTime now = OffsetDateTime.now();
        Map<ConversationSenderKey, List<UUID>> grouped = new HashMap<>();
        for (Message m : pending) {
            m.setStatus(MessageStatus.DELIVERED);
            m.setDeliveredAt(now);
            ConversationSenderKey key = new ConversationSenderKey(m.getConversation().getId(), m.getSender().getId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(m.getId());
        }
        messageRepository.saveAll(pending);

        for (Map.Entry<ConversationSenderKey, List<UUID>> entry : grouped.entrySet()) {
            eventPublisher.publishEvent(new MessagesDeliveredEvent(
                    entry.getKey().conversationId(), entry.getKey().senderId(), entry.getValue(), now));
        }
    }

    private record ConversationSenderKey(UUID conversationId, UUID senderId) {
    }

    // ------------------------------------------------------------------ guards

    private Conversation loadConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private static void requireParticipant(Conversation conversation, UUID userId) {
        boolean isParticipant = conversation.getUserOne().getId().equals(userId)
                || conversation.getUserTwo().getId().equals(userId);
        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
        }
    }

    private static UUID otherParticipantId(Conversation conversation, UUID userId) {
        requireParticipant(conversation, userId);
        return conversation.getUserOne().getId().equals(userId)
                ? conversation.getUserTwo().getId()
                : conversation.getUserOne().getId();
    }

    private static void incrementUnread(Conversation conversation, UUID recipientId) {
        if (recipientId.equals(conversation.getUserOne().getId())) {
            conversation.setUnreadCountUserOne(conversation.getUnreadCountUserOne() + 1);
        } else {
            conversation.setUnreadCountUserTwo(conversation.getUnreadCountUserTwo() + 1);
        }
    }

    private static void zeroUnread(Conversation conversation, UUID userId) {
        if (userId.equals(conversation.getUserOne().getId())) {
            conversation.setUnreadCountUserOne(0);
        } else {
            conversation.setUnreadCountUserTwo(0);
        }
    }

    private static String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
        return trimmed;
    }

    // ------------------------------------------------------------------ mapping

    private ConversationSummaryResponse toSummary(Conversation conversation, UUID viewerUserId) {
        boolean viewerIsUserOne = conversation.getUserOne().getId().equals(viewerUserId);
        AppUser other = viewerIsUserOne ? conversation.getUserTwo() : conversation.getUserOne();
        Integer unreadCount = viewerIsUserOne ? conversation.getUnreadCountUserOne() : conversation.getUnreadCountUserTwo();

        ConversationParticipantSummary participant = ConversationParticipantSummary.builder()
                .id(other.getId())
                .fullName(other.getFullName())
                .avatarUrl(other.getAvatarUrl())
                .isOnline(presenceRegistry.isOnline(other.getId()))
                .lastSeenAt(other.getLastSeenAt())
                .build();

        ConversationListingSummary listingSummary = null;
        Listing listing = conversation.getListing();
        if (listing != null) {
            listingSummary = ConversationListingSummary.builder()
                    .id(listing.getId())
                    .title(listing.getTitle())
                    .rentAmount(listing.getRentAmount())
                    .localityName(listing.getLocalityName())
                    .build();
        }

        LastMessageSummary lastMessageSummary = null;
        Page<Message> lastMessagePage = messageRepository.findByConversationIdOrderBySentAtDesc(
                conversation.getId(), PageRequest.of(0, 1));
        if (!lastMessagePage.isEmpty()) {
            Message last = lastMessagePage.getContent().get(0);
            lastMessageSummary = LastMessageSummary.builder()
                    .content(last.getContent())
                    .sentAt(last.getSentAt())
                    .status(last.getStatus())
                    .senderId(last.getSender().getId())
                    .build();
        }

        return ConversationSummaryResponse.builder()
                .id(conversation.getId())
                .participant(participant)
                .listing(listingSummary)
                .lastMessage(lastMessageSummary)
                .unreadCount(unreadCount)
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private static int compareUuids(UUID a, UUID b) {
        // Postgres compares UUIDs lexicographically by canonical hex string (unsigned byte order).
        // java.util.UUID.compareTo compares signed longs, which reverses order for high-bit hex (>= '8').
        return a.toString().compareTo(b.toString());
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSender().getId())
                .recipientId(m.getRecipient().getId())
                .content(m.getContent())
                .status(m.getStatus())
                .sentAt(m.getSentAt())
                .deliveredAt(m.getDeliveredAt())
                .readAt(m.getReadAt())
                .build();
    }
}
