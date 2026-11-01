package com.matcher.matchmate_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** POST /api/v1/chat/conversations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartConversationRequest {

    @NotNull(message = "otherUserId is required")
    private UUID otherUserId;

    /** Optional - the listing the conversation started from, if any. */
    private UUID listingId;
}
