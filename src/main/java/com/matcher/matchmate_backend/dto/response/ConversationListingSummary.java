package com.matcher.matchmate_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** The listing a conversation started from, if any. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListingSummary {
    private UUID id;
    private String title;
    private Integer rentAmount;
    private String localityName;
}
