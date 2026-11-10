package com.matcher.matchmate_backend.model.enums.chat;

/**
 * Lifecycle status of a direct message request.
 * <ul>
 *   <li>{@link #PENDING} — User A sent an initial message; waiting for User B to accept or reject.</li>
 *   <li>{@link #ACCEPTED} — Request accepted; regular bidirectional chat enabled.</li>
 *   <li>{@link #REJECTED} — Request declined; initiator cannot send further messages until allowed.</li>
 * </ul>
 */
public enum ConversationRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
