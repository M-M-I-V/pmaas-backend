package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.LoginRequest;
import dev.mmiv.pmaas.dto.LoginResponse;
import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.dto.UserResponse;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service for user management and authentication.
 * verifyUser() returns LoginResponse (token + username + role + expiresAt).
 *   getUsers() and getUserById() now return UserResponse — the password hash is never
 *   included in any response leaving this service.
 * verifyUser() accepts LoginRequest DTO, not the raw Users entity.
 *   AuthenticationException is allowed to propagate to AuthController,
 *   which catches it and returns a 401. The old "Login Failed" string return is removed.
 */
@Service
public class UsersService {

    private final UsersRepository usersRepository;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;

    @Value("${jwt.expiration-ms:28800000}")
    private long expirationMs;

    public UsersService(UsersRepository usersRepository,
                        AuthenticationManager authenticationManager,
                        JWTService jwtService) {
        this.usersRepository = usersRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    // Authentication
    /**
     * Authenticates the user and returns a structured LoginResponse.
     * Throws BadCredentialsException / LockedException on failure — these propagate
     * to AuthController which maps them to HTTP 401. The old "Login Failed" string
     * return is removed.
     */
    public LoginResponse verifyUser(LoginRequest loginRequest) {
        // Throws BadCredentialsException on invalid credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.username(),
                        loginRequest.password()
                )
        );

        Users dbUser = usersRepository.findByUsername(loginRequest.username());
        if (dbUser == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Authentication succeeded but user record not found.");
        }

        String token    = jwtService.generateToken(dbUser);
        long expiresAt  = System.currentTimeMillis() + expirationMs;

        return new LoginResponse(token, dbUser.getUsername(), dbUser.getRole().name(), expiresAt);
    }

    // ── User CRUD ─────────────────────────────────────────────────────────────

    public void createUser(Users user) {
        if (usersRepository.findByUsername(user.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists.");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        usersRepository.save(user);
    }

    /** Returns UserResponse list — password hash excluded. */
    public List<UserResponse> getUsers() {
        return usersRepository.findAll().stream()
                .map(this::toUserResponse)
                .toList();
    }

    // Returns UserResponse — password hash excluded. */
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

        if (!passwordEncoder.matches(updatedUser.getPassword(), existing.getPassword())) {
            existing.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
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