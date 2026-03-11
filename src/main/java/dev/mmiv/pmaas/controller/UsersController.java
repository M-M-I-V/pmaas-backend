package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.dto.UserResponse;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only user management endpoints.
 * GET /api/admin/users and GET /api/admin/users/{id} now return
 *   UserResponse (id, username, role) instead of the raw Users entity.
 *   The Users entity contains the bcrypt password hash; returning it exposes
 *   the hash over the network, violating data minimisation.
 * Error handling: ResponseStatusException thrown by UsersService propagates
 * naturally to Spring's default error handler (returning structured JSON with
 * status, message, and timestamp). A @RestControllerAdvice will be added in
 * Priority 4 to standardise the error response format.
 * CORS is handled globally in WebSecurityConfiguration.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsersController {

    private final UsersService usersService;

    @PostMapping("/add")
    public ResponseEntity<String> addUser(@RequestBody Users user) {
        usersService.createUser(user);
        return ResponseEntity.ok("User successfully created.");
    }

    /** Returns List<UserResponse> — password hash excluded. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(usersService.getUsers());
    }

    /** Returns UserResponse — password hash excluded. */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable int id) {
        return ResponseEntity.ok(usersService.getUserById(id));
    }

    @GetMapping("/list")
    public ResponseEntity<List<UserList>> getUsersList() {
        return ResponseEntity.ok(usersService.getUsersList());
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<String> updateUser(@PathVariable int id, @RequestBody Users user) {
        usersService.updateUser(id, user);
        return ResponseEntity.ok("User successfully updated.");
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable int id) {
        usersService.deleteUserById(id);
        return ResponseEntity.ok("User successfully deleted.");
    }
}