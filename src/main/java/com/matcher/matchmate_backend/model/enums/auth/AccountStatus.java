package com.matcher.matchmate_backend.model.enums.auth;

/**
 * Lifecycle of a self-service account.
 * <ul>
 *   <li>{@code ACTIVE} - normal.</li>
 *   <li>{@code DISABLED} - the user paused their account (POST /users/me/disable). Hidden from
 *       discovery, searches and listing visibility; every bearer token is rejected; signing in
 *       again re-activates it.</li>
 *   <li>{@code PENDING_DELETION} - the user requested deletion (DELETE /users/me). Every bearer
 *       token is rejected except one call to POST /users/me/cancel-deletion. Signing in during
 *       the grace period does NOT auto-cancel or auto-reactivate - the client shows a confirm
 *       screen. If nobody cancels before {@code scheduled_purge_at}, {@code AccountPurgeScheduler}
 *       resets the row back to {@code ACTIVE} with blank-slate data (not a hard delete - see
 *       {@code AccountPurgeService}), except for a suspended account, which the purge skips
 *       entirely so a suspension always survives a lapsed deletion window.</li>
 *   <li>{@code DELETED} - legacy value only, written by the old immediate/permanent deletion
 *       flow before this grace-period feature existed. No code path sets it going forward.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    DISABLED,
    PENDING_DELETION,
    DELETED
}
