package com.matcher.matchmate_backend.repository;

import com.matcher.matchmate_backend.model.entity.UserNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Keyed by {@code user_id}; see {@link UserNotificationPreferences}. */
public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferences, UUID> {
}
