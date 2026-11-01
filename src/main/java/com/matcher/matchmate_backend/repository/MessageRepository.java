package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.Message;
import com.matcher.matchmate_backend.model.enums.chat.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    /** For mark-read: the reader's unread/undelivered messages in this conversation. */
    List<Message> findByConversationIdAndRecipientIdAndStatusNot(UUID conversationId, UUID recipientId, MessageStatus status);

    /** For the on-reconnect delivery sweep: everything still SENT while this user was offline. */
    List<Message> findByRecipientIdAndStatus(UUID recipientId, MessageStatus status);
}
