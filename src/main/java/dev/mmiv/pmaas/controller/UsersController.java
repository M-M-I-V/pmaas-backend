package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsersController {

    private final UsersService usersService;

    @PostMapping("/add")
    public ResponseEntity<String> addUser(@RequestBody Users user) {
        usersService.createUser(user);
        return ResponseEntity.ok("User successfully created.");
    }

    @GetMapping
    public ResponseEntity<List<Users>> getUsers() {
        return ResponseEntity.ok(usersService.getUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Users> getUserById(@PathVariable int id) {
        Users user = usersService.getUserById(id);
        return user != null
                ? ResponseEntity.ok(user)
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public ResponseEntity<List<UserList>> getUsersList() {
        return ResponseEntity.ok(usersService.getUsersList());
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<String> updateUser(@PathVariable int id, @RequestBody Users user) {
        try {
            usersService.updateUser(id, user);
            return ResponseEntity.ok("User successfully updated.");
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found", e);
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable int id) {
        try {
            usersService.deleteUserById(id);
            return ResponseEntity.ok("User successfully deleted.");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", e);
        }
    }
}