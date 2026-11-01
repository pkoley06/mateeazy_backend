package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The other participant in a conversation, as seen from the viewer's side. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipantSummary {
    private UUID id;
    private String fullName;
    private String avatarUrl;
    /** Live from {@code PresenceRegistry}, never the persisted mirror. */
    private Boolean isOnline;
    private OffsetDateTime lastSeenAt;
}
