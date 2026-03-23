package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Integer> {

    /** Legacy lookup by username (used by existing admin endpoints). */
    Users findByUsername(String username);

    /**
     * OAuth2 primary lookup — by Google's stable subject identifier.
     * Use this first; it survives email or display name changes in Google Workspace.
     */
    Optional<Users> findByGoogleSub(String googleSub);

    /**
     * OAuth2 fallback lookup — by institutional email.
     * Used when a user exists in the DB (created before OAuth2 migration)
     * but does not yet have a googleSub recorded.
     */
    Optional<Users> findByEmail(String email);

    boolean existsByEmail(String email);
}