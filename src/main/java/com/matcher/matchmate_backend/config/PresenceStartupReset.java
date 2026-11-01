package com.matcher.matchmate_backend.config;

import com.matcher.matchmate_backend.repository.AppUserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Defensive reset on every startup: the in-memory {@code PresenceRegistry} always starts
 * empty, so no user is actually online right after a (re)deploy - but {@code is_online} on
 * app_users is only a persisted mirror from before the restart and may still say otherwise
 * (e.g. the process was killed instead of shutting down cleanly). Without this, those rows
 * would report "online" forever until that user's next socket connect/disconnect.
 */
@Component
public class PresenceStartupReset implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PresenceStartupReset.class);

    private final AppUserRepository userRepository;

    public PresenceStartupReset(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.resetAllOnlineStatus();
        log.info("[Presence] Reset stale online flags on startup.");
    }
}
