package com.matcher.matchmate_backend.scheduler;

import com.matcher.matchmate_backend.model.entity.AppUser;
import com.matcher.matchmate_backend.model.enums.auth.AccountStatus;
import com.matcher.matchmate_backend.repository.AppUserRepository;
import com.matcher.matchmate_backend.service.AccountPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Daily sweep for accounts whose 30-day (admin-configurable) deletion grace period has lapsed.
 * First scheduled job in this codebase - no existing convention to follow.
 */
@Component
public class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final AppUserRepository userRepository;
    private final AccountPurgeService accountPurgeService;

    public AccountPurgeScheduler(AppUserRepository userRepository, AccountPurgeService accountPurgeService) {
        this.userRepository = userRepository;
        this.accountPurgeService = accountPurgeService;
    }

    /** Daily at 03:00 server time - a low-traffic window. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredAccounts() {
        List<AppUser> due = userRepository.findByAccountStatusAndScheduledPurgeAtBefore(
                AccountStatus.PENDING_DELETION, OffsetDateTime.now());

        if (due.isEmpty()) return;
        log.info("AccountPurgeScheduler: {} account(s) due for purge", due.size());

        for (AppUser user : due) {
            try {
                accountPurgeService.purgeUser(user.getId());
            } catch (Exception e) {
                // One failing user's purge must never abort the batch for the rest.
                log.error("Failed to purge account {}: {}", user.getId(), e.getMessage(), e);
            }
        }
    }
}
