package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.ConversationListingSummary;
import com.matcher.matchmate_backend.dto.response.ConversationParticipantSummary;
import com.matcher.matchmate_backend.dto.response.ConversationSummaryResponse;
import com.matcher.matchmate_backend.dto.response.LastMessageSummary;
import com.matcher.matchmate_backend.dto.response.MessageReactionResponse;
import com.matcher.matchmate_backend.dto.response.MessageResponse;
import com.matcher.matchmate_backend.dto.response.PageResponse;
import com.matcher.matchmate_backend.event.MessageSentEvent;
import com.matcher.matchmate_backend.event.MessageUpdatedEvent;
import com.matcher.matchmate_backend.event.MessagesDeliveredEvent;
import com.matcher.matchmate_backend.event.MessagesReadEvent;
import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.entity.Conversation;
import com.matcher.matchmate_backend.model.entity.Listing;
import com.matcher.matchmate_backend.model.entity.Message;
import com.matcher.matchmate_backend.model.entity.MessageReaction;
import com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus;
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
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final UserBlockRepository blockRepository;
    private final PresenceRegistry presenceRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final StorageService storageService;
    private final ChatMediaQuotaService mediaQuotaService;
    private final com.matcher.matchmate_backend.repository.MessageReactionRepository messageReactionRepository;

    public ChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AppUserRepository userRepository,
            ListingRepository listingRepository,
            UserBlockRepository blockRepository,
            PresenceRegistry presenceRegistry,
            ApplicationEventPublisher eventPublisher,
            StorageService storageService,
            ChatMediaQuotaService mediaQuotaService,
            com.matcher.matchmate_backend.repository.MessageReactionRepository messageReactionRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.blockRepository = blockRepository;
        this.presenceRegistry = presenceRegistry;
        this.eventPublisher = eventPublisher;
        this.storageService = storageService;
        this.mediaQuotaService = mediaQuotaService;
        this.messageReactionRepository = messageReactionRepository;
    }

    // ------------------------------------------------------------------ conversations

    @Transactional
    public ConversationSummaryResponse getOrCreateConversation(UUID requesterId, UUID otherUserId, UUID listingId) {
        if (requesterId.equals(otherUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start a conversation with yourself");
        }
        requireNotBlocked(requesterId, otherUserId);
        boolean requesterFirst = compareUuids(requesterId, otherUserId) < 0;
        UUID userOneId = requesterFirst ? requesterId : otherUserId;
        UUID userTwoId = requesterFirst ? otherUserId : requesterId;

        Conversation conversation = conversationRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                .orElseGet(() -> createConversation(userOneId, userTwoId, listingId, requesterId));

        return toSummary(conversation, requesterId);
    }

    private Conversation createConversation(UUID userOneId, UUID userTwoId, UUID listingId, UUID initiatorUserId) {
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
                .requestStatus(com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus.PENDING)
                .initiatorUserId(initiatorUserId)
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional
    public ConversationSummaryResponse acceptConversationRequest(UUID conversationId, UUID userId) {
        Conversation conversation = loadConversation(conversationId);
        requireParticipant(conversation, userId);
        conversation.setRequestStatus(com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus.ACCEPTED);
        Conversation saved = conversationRepository.save(conversation);
        UUID otherId = otherParticipantId(saved, userId);
        eventPublisher.publishEvent(new com.matcher.matchmate_backend.event.ConversationRequestEvent(
                conversationId, saved.getInitiatorUserId(), otherId, com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus.ACCEPTED));
        return toSummary(saved, userId);
    }

    @Transactional
    public ConversationSummaryResponse rejectConversationRequest(UUID conversationId, UUID userId) {
        Conversation conversation = loadConversation(conversationId);
        requireParticipant(conversation, userId);
        conversation.setRequestStatus(com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus.REJECTED);
        zeroUnread(conversation, userId);
        Conversation saved = conversationRepository.save(conversation);
        UUID otherId = otherParticipantId(saved, userId);
        eventPublisher.publishEvent(new com.matcher.matchmate_backend.event.ConversationRequestEvent(
                conversationId, saved.getInitiatorUserId(), otherId, com.matcher.matchmate_backend.model.enums.chat.ConversationRequestStatus.REJECTED));
        return toSummary(saved, userId);
    }

    @Transactional
    public List<ConversationSummaryResponse> listConversations(UUID userId) {
        // Conversations with someone the viewer blocked, or who blocked the viewer, are hidden
        // (they come back on their own if the block is lifted).
        Set<UUID> hidden = new HashSet<>(blockRepository.findBlockedUserIdsByBlockerId(userId));
        hidden.addAll(blockRepository.findBlockerIdsByBlockedId(userId));

        List<Conversation> conversations = conversationRepository.findAllInvolvingUser(userId);
        return conversations.stream()
                .filter(c -> !hidden.contains(otherParticipantId(c, userId)))
                .map(c -> toSummary(c, userId))
                .toList();
    }

    // ------------------------------------------------------------------ messages

    @Transactional
    public PageResponse<MessageResponse> getMessages(UUID conversationId, UUID requesterId, Pageable pageable) {
        Conversation conversation = loadConversation(conversationId);
        requireParticipant(conversation, requesterId);
        Page<Message> page = messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);
        List<UUID> messageIds = page.getContent().stream().map(Message::getId).toList();
        Map<UUID, List<MessageReaction>> reactionsByMessageId = new HashMap<>();
        if (!messageIds.isEmpty()) {
            for (MessageReaction r : messageReactionRepository.findByMessageIdIn(messageIds)) {
                reactionsByMessageId.computeIfAbsent(r.getMessage().getId(), k -> new ArrayList<>()).add(r);
            }
        }
        return PageResponse.of(page, m -> toMessageResponse(m, mapReactions(reactionsByMessageId.getOrDefault(m.getId(), List.of()))));
    }

    /** Older callers: a plain text message. */
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, String content) {
        return sendMessage(conversationId, senderId, content, MessageType.TEXT, null, null);
    }

    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId, UUID senderId, String content, MessageType type, List<String> attachmentUrls
    ) {
        return sendMessage(conversationId, senderId, content, type, attachmentUrls, null);
    }

    /**
     * Sends a TEXT, IMAGE (1-5 photos, counted against the daily quota) or GIF message, with optional reply context.
     */
    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId, UUID senderId, String content, MessageType type, List<String> attachmentUrls, UUID replyToMessageId
    ) {
        MessageType messageType = type == null ? MessageType.TEXT : type;
        List<String> attachments = validateAttachments(messageType, attachmentUrls);
        String trimmed = messageType.isMedia() ? validateCaption(content) : validateContent(content);
        Conversation conversation = loadConversation(conversationId);
        UUID recipientId = otherParticipantId(conversation, senderId);
        requireNotBlocked(senderId, recipientId);

        // Check request status
        if (conversation.getRequestStatus() == ConversationRequestStatus.REJECTED) {
            if (senderId.equals(conversation.getInitiatorUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Message request was declined. You cannot send further messages.");
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You declined this message request. Please allow it first to start chatting.");
            }
        } else if (conversation.getRequestStatus() == ConversationRequestStatus.PENDING) {
            if (conversation.getInitiatorUserId() == null) {
                conversation.setInitiatorUserId(senderId);
            }
            if (senderId.equals(conversation.getInitiatorUserId())) {
                boolean alreadySent = messageRepository.existsByConversationId(conversationId);
                if (alreadySent) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You can only send 1 message with a message request. Please wait for the recipient to accept.");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Please accept the message request to start chatting.");
            }
        }

        if (messageType == MessageType.IMAGE) {
            mediaQuotaService.requireAllowance(senderId, attachments.size());
        }

        AppUser sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        AppUser recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String replyToSenderName = null;
        String replyToContent = null;
        if (replyToMessageId != null) {
            Message replyTo = messageRepository.findById(replyToMessageId).orElse(null);
            if (replyTo != null && replyTo.getConversation().getId().equals(conversationId)) {
                replyToSenderName = replyTo.getSender().getFullName();
                if (Boolean.TRUE.equals(replyTo.getIsDeleted())) {
                    replyToContent = "This message was deleted";
                } else if (replyTo.getMessageType() == MessageType.IMAGE) {
                    replyToContent = replyTo.getContent().isBlank() ? "📷 Photo" : "📷 " + replyTo.getContent();
                } else if (replyTo.getMessageType() == MessageType.GIF) {
                    replyToContent = "GIF";
                } else {
                    replyToContent = replyTo.getContent().length() > 150
                            ? replyTo.getContent().substring(0, 150) + "..."
                            : replyTo.getContent();
                }
            }
        }

        boolean recipientOnline = presenceRegistry.isOnline(recipientId);
        OffsetDateTime now = OffsetDateTime.now();

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .recipient(recipient)
                .content(trimmed)
                .messageType(messageType)
                .attachmentUrls(new ArrayList<>(attachments))
                .status(recipientOnline ? MessageStatus.DELIVERED : MessageStatus.SENT)
                .deliveredAt(recipientOnline ? now : null)
                .replyToMessageId(replyToMessageId)
                .replyToContent(replyToContent)
                .replyToSenderName(replyToSenderName)
                .isEdited(false)
                .isDeleted(false)
                .build();
        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        incrementUnread(conversation, recipientId);
        conversationRepository.save(conversation);

        eventPublisher.publishEvent(new MessageSentEvent(
                conversation.getId(), saved.getId(), senderId, recipientId,
                trimmed, messageType, List.copyOf(attachments), saved.getSentAt(), saved.getStatus(),
                replyToMessageId, replyToContent, replyToSenderName));

        return toMessageResponse(saved, List.of());
    }

    @Transactional
    public MessageResponse editMessage(UUID messageId, UUID userId, String newContent) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own messages");
        }
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleted messages cannot be edited");
        }
        String trimmed = validateContent(newContent);
        message.setContent(trimmed);
        message.setIsEdited(true);
        message.setEditedAt(OffsetDateTime.now());
        Message saved = messageRepository.save(message);

        List<MessageReactionResponse> reactions = mapReactions(messageReactionRepository.findByMessageId(messageId));
        UUID otherId = otherParticipantId(message.getConversation(), userId);
        eventPublisher.publishEvent(new MessageUpdatedEvent(
                message.getConversation().getId(), message.getId(), userId, otherId,
                trimmed, true, saved.getEditedAt(), false, null, reactions));

        return toMessageResponse(saved, reactions);
    }

    @Transactional
    public MessageResponse deleteMessage(UUID messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getSender().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own messages");
        }
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            return toMessageResponse(message);
        }
        OffsetDateTime now = OffsetDateTime.now();
        long minutes = Duration.between(message.getSentAt(), now).toMinutes();
        if (minutes > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Messages can only be deleted within 5 minutes of sending");
        }
        message.setIsDeleted(true);
        message.setDeletedAt(now);
        message.setContent("This message was deleted");
        message.setAttachmentUrls(new ArrayList<>());
        Message saved = messageRepository.save(message);

        List<MessageReaction> reactions = messageReactionRepository.findByMessageId(messageId);
        if (!reactions.isEmpty()) {
            messageReactionRepository.deleteAll(reactions);
        }

        UUID otherId = otherParticipantId(message.getConversation(), userId);
        eventPublisher.publishEvent(new MessageUpdatedEvent(
                message.getConversation().getId(), message.getId(), userId, otherId,
                "This message was deleted", false, null, true, now, List.of()));

        return toMessageResponse(saved, List.of());
    }

    @Transactional
    public MessageResponse toggleReaction(UUID messageId, UUID userId, String emoji) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        requireParticipant(message.getConversation(), userId);
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot react to a deleted message");
        }
        if (emoji == null || emoji.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Emoji cannot be blank");
        }
        String trimmedEmoji = emoji.trim();

        Optional<MessageReaction> existing = messageReactionRepository.findByMessageIdAndUserId(messageId, userId);
        if (existing.isPresent()) {
            if (existing.get().getEmoji().equals(trimmedEmoji)) {
                messageReactionRepository.delete(existing.get());
            } else {
                existing.get().setEmoji(trimmedEmoji);
                messageReactionRepository.save(existing.get());
            }
        } else {
            AppUser user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            MessageReaction mr = MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .emoji(trimmedEmoji)
                    .build();
            messageReactionRepository.save(mr);
        }

        List<MessageReactionResponse> reactions = mapReactions(messageReactionRepository.findByMessageId(messageId));
        UUID otherId = otherParticipantId(message.getConversation(), userId);
        eventPublisher.publishEvent(new MessageUpdatedEvent(
                message.getConversation().getId(), message.getId(), userId, otherId,
                message.getContent(), message.getIsEdited(), message.getEditedAt(),
                message.getIsDeleted(), message.getDeletedAt(), reactions));

        return toMessageResponse(message, reactions);
    }

    private List<String> validateAttachments(MessageType type, List<String> urls) {
        List<String> cleaned = urls == null
                ? List.of()
                : urls.stream().filter(u -> u != null && !u.isBlank()).map(String::trim).toList();
        switch (type) {
            case TEXT -> {
                if (!cleaned.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A text message cannot carry attachments");
                }
            }
            case IMAGE -> {
                if (cleaned.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one photo");
                }
                if (cleaned.size() > ChatMediaQuotaService.DAILY_PHOTO_LIMIT) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "At most " + ChatMediaQuotaService.DAILY_PHOTO_LIMIT + " photos per message");
                }
                for (String url : cleaned) {
                    if (!storageService.isManagedUrl(url)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photos must be uploaded through the app");
                    }
                }
            }
            case GIF -> {
                if (cleaned.size() != 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A GIF message carries exactly one GIF");
                }
                String url = cleaned.get(0);
                if (!storageService.isManagedUrl(url) && !GifService.isTrustedGifUrl(url)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That GIF link isn't allowed");
                }
            }
        }
        return cleaned;
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

    private void requireNotBlocked(UUID a, UUID b) {
        if (blockRepository.existsBlockBetween(a, b)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't message this user");
        }
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

    /** Media messages may carry an optional caption; it is stored in {@code content}. */
    private static String validateCaption(String content) {
        if (content == null || content.isBlank()) return "";
        String trimmed = content.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caption cannot exceed " + MAX_MESSAGE_LENGTH + " characters");
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

        // The room card pinned in the thread: the other person's newest open room, so a host
        // with several rooms shows exactly one (the latest). When they have none - the viewer
        // is the host, or the room was since closed - fall back to the room the chat started from.
        ConversationListingSummary listingSummary = null;
        Listing listing = listingRepository
                .findFirstByHostUserIdAndIsAvailableTrueOrderByCreatedAtDesc(other.getId())
                .orElse(conversation.getListing());
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
                    .messageType(last.getMessageType())
                    .attachmentCount(last.getAttachmentUrls() == null ? 0 : last.getAttachmentUrls().size())
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
                .requestStatus(conversation.getRequestStatus())
                .initiatorUserId(conversation.getInitiatorUserId())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private static int compareUuids(UUID a, UUID b) {
        // Postgres compares UUIDs lexicographically by canonical hex string (unsigned byte order).
        // java.util.UUID.compareTo compares signed longs, which reverses order for high-bit hex (>= '8').
        return a.toString().compareTo(b.toString());
    }

    private List<MessageReactionResponse> mapReactions(List<MessageReaction> list) {
        if (list == null || list.isEmpty()) return List.of();
        Map<String, List<UUID>> grouped = new LinkedHashMap<>();
        for (MessageReaction r : list) {
            grouped.computeIfAbsent(r.getEmoji(), k -> new ArrayList<>()).add(r.getUser().getId());
        }
        return grouped.entrySet().stream()
                .map(e -> MessageReactionResponse.builder()
                        .emoji(e.getKey())
                        .count(e.getValue().size())
                        .userIds(e.getValue())
                        .build())
                .toList();
    }

    public MessageResponse toMessageResponse(Message m) {
        List<MessageReaction> reactions = messageReactionRepository.findByMessageId(m.getId());
        return toMessageResponse(m, mapReactions(reactions));
    }

    public MessageResponse toMessageResponse(Message m, List<MessageReactionResponse> reactions) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSender().getId())
                .recipientId(m.getRecipient().getId())
                .content(m.getContent())
                .messageType(m.getMessageType() == null ? MessageType.TEXT : m.getMessageType())
                .attachmentUrls(m.getAttachmentUrls() == null ? List.of() : List.copyOf(m.getAttachmentUrls()))
                .status(m.getStatus())
                .replyToMessageId(m.getReplyToMessageId())
                .replyToContent(m.getReplyToContent())
                .replyToSenderName(m.getReplyToSenderName())
                .isEdited(Boolean.TRUE.equals(m.getIsEdited()))
                .editedAt(m.getEditedAt())
                .isDeleted(Boolean.TRUE.equals(m.getIsDeleted()))
                .reactions(reactions != null ? reactions : List.of())
                .sentAt(m.getSentAt())
                .deliveredAt(m.getDeliveredAt())
                .readAt(m.getReadAt())
                .build();
    }
}
