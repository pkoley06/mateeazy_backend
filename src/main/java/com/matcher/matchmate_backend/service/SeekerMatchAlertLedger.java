package com.matcher.matchmate_backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Remembers which hosts were already alerted about which room seeker ({@code seeker_match_alerts}, V33),
 * so the "new roommate match" push goes out once per Room Finder session instead of on every
 * save that re-runs the fan-out (the room-seeker toggle and the alert-area save both do, back to back).
 */
@Component
public class SeekerMatchAlertLedger {

    /** Even across sessions (toggling Room Finder off and on), a host hears about the same seeker at most this often. */
    static final Duration MIN_REPEAT_INTERVAL = Duration.ofHours(24);

    private final JdbcTemplate jdbc;

    public SeekerMatchAlertLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically claims the right to alert {@code hostId} about {@code seekerId}. Returns {@code true}
     * when the caller should send the alert: no earlier alert exists, or the last one predates both the
     * seeker's current Room Finder session ({@code sessionStart}) and {@link #MIN_REPEAT_INTERVAL}.
     * Concurrent callers for the same pair are serialised by the primary key, so only one wins.
     */
    public boolean claim(UUID hostId, UUID seekerId, OffsetDateTime sessionStart) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime repeatCutoff = now.minus(MIN_REPEAT_INTERVAL);
        // Re-alert only when the last alert is older than BOTH the session start and the repeat interval.
        OffsetDateTime cutoff = (sessionStart != null && sessionStart.isBefore(repeatCutoff)) ? sessionStart : repeatCutoff;

        int rows = jdbc.update("""
                INSERT INTO seeker_match_alerts (host_user_id, seeker_user_id, notified_at)
                VALUES (?, ?, ?)
                ON CONFLICT (host_user_id, seeker_user_id) DO UPDATE
                    SET notified_at = EXCLUDED.notified_at
                    WHERE seeker_match_alerts.notified_at < ?
                """, hostId, seekerId, now, cutoff);
        return rows > 0;
    }
}
