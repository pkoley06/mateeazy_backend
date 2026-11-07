package com.matcher.matchmate_backend.service;

import com.matcher.matchmate_backend.dto.response.ChatMediaQuotaResponse;
import com.matcher.matchmate_backend.model.enums.chat.MessageType;
import com.matcher.matchmate_backend.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * "5 photos a day" for chat. Counted off the IMAGE messages the user actually sent today
 * (UTC day), not off uploads, so an abandoned upload never eats into the allowance and
 * a second device sees the same number. GIFs are free: a Tenor GIF is only a URL and an
 * uploaded one is a single small file.
 */
@Service
public class ChatMediaQuotaService {

    public static final int DAILY_PHOTO_LIMIT = 5;

    private final MessageRepository messageRepository;

    public ChatMediaQuotaService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public int usedToday(UUID userId) {
        OffsetDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        Long used = messageRepository.countAttachmentsSentSince(userId, MessageType.IMAGE.name(), startOfDay);
        return used == null ? 0 : used.intValue();
    }

    public ChatMediaQuotaResponse quotaFor(UUID userId) {
        int used = usedToday(userId);
        return ChatMediaQuotaResponse.builder()
                .limit(DAILY_PHOTO_LIMIT)
                .used(used)
                .remaining(Math.max(0, DAILY_PHOTO_LIMIT - used))
                .build();
    }

    /** Throws 429 when sending {@code count} more photos would exceed today's allowance. */
    public void requireAllowance(UUID userId, int count) {
        int used = usedToday(userId);
        if (used + count > DAILY_PHOTO_LIMIT) {
            int remaining = Math.max(0, DAILY_PHOTO_LIMIT - used);
            String message = remaining == 0
                    ? "You've shared " + DAILY_PHOTO_LIMIT + " photos today. More tomorrow!"
                    : "You can share " + remaining + " more photo" + (remaining == 1 ? "" : "s") + " today.";
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }
}
