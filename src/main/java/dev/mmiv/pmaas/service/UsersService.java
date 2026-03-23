package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.AdminCreateUserRequest;
import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.dto.UserResponse;
import dev.mmiv.pmaas.entity.Role;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service for admin user management.
 *
 * Authentication is handled externally by Spring Security OAuth2 (Google).
 * This service handles only the admin CRUD operations: pre-provisioning
 * users, updating roles, and deactivating accounts.
 *
 * createUser() now accepts AdminCreateUserRequest to prevent admins from
 * directly setting OAuth2-managed fields (googleSub, avatarUrl, name).
 * Those fields are populated exclusively by OAuth2LoginSuccessHandler.
 */
@Service
public class UsersService {

    private final UsersRepository usersRepository;

    public UsersService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    /**
     * Pre-provisions a user account for Google OAuth2 login.
     *
     * Creates a Users record with:
     *   - email and username set to the provided email (OAuth2 convention)
     *   - role set to the provided role
     *   - enabled set per the request (default true)
     *   - googleSub, name, avatarUrl left null (populated on first login)
     *
     * The user can log in as soon as this record exists and they authenticate
     * with the matching @mcst.edu.ph Google account.
     */
    public void createUser(AdminCreateUserRequest request) {
        if (usersRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A user with email '" + request.email() + "' already exists.");
        }

        Users user = new Users();
        user.setEmail(request.email());
        user.setUsername(request.email());  // username mirrors email for OAuth2 users
        user.setRole(request.role());
        user.setEnabled(request.isEnabled());
        // googleSub, name, avatarUrl are intentionally left null.
        // They are set by OAuth2LoginSuccessHandler on first login.

        usersRepository.save(user);
    }

    public List<UserResponse> getUsers() {
        return usersRepository.findAll().stream()
                .map(this::toUserResponse)
                .toList();
    }

    public UserResponse getUserById(int id) {
        return usersRepository.findById(id)
                .map(this::toUserResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));
    }

    public List<UserList> getUsersList() {
        return usersRepository.findAll().stream()
                .map(u -> new UserList(u.getId(), u.getUsername(), u.getRole().name()))
                .toList();
    }

    /**
     * Updates a user's role and/or enabled status.
     *
     * Username and email are intentionally not updatable here — they are
     * tied to the Google identity. Changing them would break OAuth2 lookup.
     * To change a user's email, delete and re-provision.
     *
     * @param role    new role, or null to leave unchanged
     * @param enabled new enabled status, or null to leave unchanged
     */
    public void updateUser(int id, Role role, Boolean enabled) {
        Users existing = usersRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        if (role != null) {
            existing.setRole(role);
        }
        if (enabled != null) {
            existing.setEnabled(enabled);
        }

        usersRepository.save(existing);
    }

    public void deleteUserById(int id) {
        if (!usersRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        usersRepository.deleteById(id);
    }

    private UserResponse toUserResponse(Users user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole().name());
    }
}