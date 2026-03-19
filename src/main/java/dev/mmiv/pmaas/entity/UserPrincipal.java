package dev.mmiv.pmaas.entity;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal wrapper for the Users entity.
 * isEnabled() now delegates to Users.isEnabled() instead of returning
 * hardcoded true. When an admin sets a user's enabled field to false, Spring Security
 * will reject that user's authentication attempt with a DisabledException before
 * any password check occurs.
 * The other three status methods (isAccountNonExpired, isAccountNonLocked,
 * isCredentialsNonExpired) remain true for now. To activate them:
 *   - Add accountNonExpired / accountNonLocked / credentialsNonExpired fields
 *     to Users and follow the same pattern as isEnabled.
 *   - isAccountNonLocked is useful for implementing automatic lockout after N
 *     failed login attempts (a future enhancement alongside the Bucket4j rate limiter).
 */
public class UserPrincipal implements UserDetails {

    private final Users user;

    public UserPrincipal(Users user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        // All accounts are currently non-expiring.
        // Add an expiresAt field to Users to support time-bound accounts (e.g. interns).
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Not locked by default.
        // Future: set to false after N consecutive failed login attempts.
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // Credentials do not expire.
        // Future: add a passwordChangedAt field and return false if > 90 days old.
        return true;
    }

    @Override
    public boolean isEnabled() {
        // S-14 FIX: delegates to the entity field instead of hardcoded true.
        // Disabled users are rejected at the Spring Security layer before any
        // business logic runs. The audit trail for their past actions is preserved.
        return user.isEnabled();
    }
}
