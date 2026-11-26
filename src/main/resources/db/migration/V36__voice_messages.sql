-- Voice messages (message_type = 'AUDIO'): the clip's length and its loudness bars,
-- so a bubble can draw its waveform and duration before the clip is downloaded.
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS media_duration_ms INTEGER,
    ADD COLUMN IF NOT EXISTS waveform          VARCHAR(64);
