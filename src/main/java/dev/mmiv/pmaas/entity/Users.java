package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Added boolean `enabled` field.
 * Previously all four UserDetails status methods in UserPrincipal returned hardcoded
 * `true`, making it impossible to disable an account without deleting it entirely.
 * Deleting a user record would also cascade-delete or orphan audit logs referencing
 * that username, breaking the integrity chain.
 * The `enabled` field defaults to true so all existing users remain active after
 * the database migration. An ADMIN can set enabled=false via the update endpoint
 * to deactivate a departed employee's account without losing their audit history.
 * DATABASE MIGRATION (run before deploying):
 *   ALTER TABLE users ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1;
 * ADMIN USAGE — deactivate a user:
 *   PUT /api/admin/users/update/{id}  →  { "enabled": false }
 *   Spring Security will reject login immediately via UserPrincipal.isEnabled().
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    /**
     * Controls whether the account can authenticate.
     * Defaults to true (active). Set to false to lock a user out without deleting them.
     * Checked by UserPrincipal.isEnabled() on every authentication attempt.
     */
    @Column(nullable = false)
    private boolean enabled = true;
}
