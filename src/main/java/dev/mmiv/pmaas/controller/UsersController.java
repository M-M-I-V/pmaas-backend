package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.AdminCreateUserRequest;
import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.dto.UserResponse;
import dev.mmiv.pmaas.entity.Role;
import dev.mmiv.pmaas.service.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only user management endpoints.
 *
 * GET /api/admin/users and GET /api/admin/users/{id} return UserResponse
 * (id, username, role) — never the raw Users entity which would expose
 * OAuth2 identity fields.
 *
 * POST /api/admin/users/add now accepts AdminCreateUserRequest instead of
 * the raw Users entity. This prevents an admin from setting OAuth2-managed
 * fields (googleSub, avatarUrl) directly, which could corrupt identity lookups.
 * See AdminCreateUserRequest for the full explanation.
 *
 * PUT /api/admin/users/update/{id} accepts an inline request object with
 * only the admin-settable update fields: role and enabled.
 * Username and email are immutable after creation (tied to the Google identity).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsersController {

    private final UsersService usersService;

    /**
     * Pre-provisions a new user account.
     * The admin supplies email + role. The user's Google identity fields
     * (googleSub, name, avatarUrl) are populated on their first login.
     */
    @PostMapping("/add")
    public ResponseEntity<String> addUser(@Valid @RequestBody AdminCreateUserRequest request) {
        usersService.createUser(request);
        return ResponseEntity.ok("User successfully created. " +
                "They may log in with their @mcst.edu.ph Google account.");
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(usersService.getUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable int id) {
        return ResponseEntity.ok(usersService.getUserById(id));
    }

    @GetMapping("/list")
    public ResponseEntity<List<UserList>> getUsersList() {
        return ResponseEntity.ok(usersService.getUsersList());
    }

    /**
     * Updates a user's role and/or enabled status.
     * Username and email are NOT updatable — they are tied to the Google identity.
     * To change a user's email, delete and re-provision the account.
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<String> updateUser(@PathVariable int id,
                                             @RequestBody AdminUpdateUserRequest request) {
        usersService.updateUser(id, request.role(), request.enabled());
        return ResponseEntity.ok("User successfully updated.");
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable int id) {
        usersService.deleteUserById(id);
        return ResponseEntity.ok("User successfully deleted.");
    }

    /**
     * Inline record for the update request.
     * Only role and enabled are updatable by an admin.
     * Both fields are optional — null means "no change".
     */
    record AdminUpdateUserRequest(Role role, Boolean enabled) {}
}