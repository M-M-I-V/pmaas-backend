package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.dto.UserResponse;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service for user management.
 * Authentication is now handled externally by Spring Security OAuth2 (Google)
 * and OAuth2LoginSuccessHandler.
 */
@Service
public class UsersService {

    private final UsersRepository usersRepository;

    // JWTService and AuthenticationManager have been removed
    // because login/token generation is now handled in the OAuth2 handlers.
    public UsersService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    // User CRUD

    public void createUser(Users user) {
        if (usersRepository.findByUsername(user.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }

        // Passwords are no longer hashed or stored here.
        // Google handles authentication.
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found."));
    }

    public List<UserList> getUsersList() {
        return usersRepository.findAll().stream()
                .map(u -> new UserList(u.getId(), u.getUsername(), u.getRole().name()))
                .toList();
    }

    public void updateUser(int id, Users updatedUser) {
        Users existing = usersRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found."));

        Users conflict = usersRepository.findByUsername(updatedUser.getUsername());
        if (conflict != null && conflict.getId() != id) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use.");
        }

        existing.setUsername(updatedUser.getUsername());
        existing.setRole(updatedUser.getRole());

        // Legacy password update logic has been removed.

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