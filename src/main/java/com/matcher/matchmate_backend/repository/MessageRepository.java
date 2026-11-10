package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Message;
import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    boolean existsByConversationId(UUID conversationId);

    Page<Message> findByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    /** For mark-read: the reader's unread/undelivered messages in this conversation. */
    List<Message> findByConversationIdAndRecipientIdAndStatusNot(UUID conversationId, UUID recipientId, MessageStatus status);

    /** For the on-reconnect delivery sweep: everything still SENT while this user was offline. */
    List<Message> findByRecipientIdAndStatus(UUID recipientId, MessageStatus status);

    /** Daily photo quota: how many attachments of {@code messageType} this user sent since {@code since}. */
    @Query(value = """
        SELECT COALESCE(SUM(cardinality(m.attachment_urls)), 0)
        FROM messages m
        WHERE m.sender_id = :senderId
          AND m.message_type = :messageType
          AND m.sent_at >= :since
        """, nativeQuery = true)
    Long countAttachmentsSentSince(
            @Param("senderId") UUID senderId,
            @Param("messageType") String messageType,
            @Param("since") OffsetDateTime since);
}
