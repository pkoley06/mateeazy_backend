package com.matcher.matchmate_backend.model.enums.chat;

/**
 * What a chat message carries. {@link #TEXT} uses {@code content}; {@link #IMAGE} carries one
 * to five uploaded photo URLs sent together (drawn as one grouped bubble); {@link #GIF} carries
 * exactly one animated GIF URL (an uploaded file or a Tenor result).
 */
public enum MessageType {
    TEXT,
    IMAGE,
    GIF,
    /** A voice message: one recorded clip (AAC in an MP4/M4A container). */
    AUDIO;

    public boolean isMedia() {
        return this != TEXT;
    }
}
