package com.matcher.matchmate_backend.websocket;

import com.matcher.matchmate_backend.dto.response.ListingCommentEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Single place that knows the comment topic layout: {@code /topic/rooms/{roomId}/comments}. */
@Component
public class ListingCommentBroadcaster {

    public static final String TOPIC_PREFIX = "/topic/rooms/";
    public static final String TOPIC_SUFFIX = "/comments";

    private final SimpMessagingTemplate messagingTemplate;

    public ListingCommentBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public static String topicFor(UUID roomId) {
        return TOPIC_PREFIX + roomId + TOPIC_SUFFIX;
    }

    public void broadcast(UUID roomId, ListingCommentEvent event) {
        messagingTemplate.convertAndSend(topicFor(roomId), event);
    }
}
