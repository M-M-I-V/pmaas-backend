package dev.mmiv.pmaas.dto;

/**
 * Lightweight user projection for the admin users list table.
 *
 * Added the enabled field.
 *
 * The previous version returned only id, username, and role. The admin users
 * list page had no way to distinguish active accounts from suspended ones —
 * the table showed all users identically regardless of their enabled status.
 * An admin would need to click into each user to discover whether an account
 * was suspended, which is impractical for routine access audits.
 *
 * Adding enabled here allows the users list to render an Active/Suspended
 * badge in the status column without a separate per-user API call.
 */
public record UserList(int id, String username, String role, boolean enabled) {}
