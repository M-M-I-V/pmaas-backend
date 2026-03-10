package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.UserList;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UsersService {

    UsersRepository usersRepository;
    AuthenticationManager authenticationManager;
    JWTService jwtService;

    public UsersService(UsersRepository usersRepository,
                        AuthenticationManager authenticationManager,
                        JWTService jwtService) {
        this.usersRepository = usersRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder(10);

    public String verifyUser(Users user) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword())
        );

        if (authentication.isAuthenticated()) {
            Users dbUser = usersRepository.findByUsername(user.getUsername());
            if (dbUser == null) {
                throw new RuntimeException("User not found");
            }
            return jwtService.generateToken(dbUser);
        } else {
            return "Login Failed";
        }
    }

    public void createUser(Users user) {
        if (usersRepository.findByUsername(user.getUsername()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        usersRepository.save(user);
    }

    public List<Users> getUsers() {
        return usersRepository.findAll();
    }

    public Users getUserById(int id) {
        return usersRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<UserList> getUsersList() {
        return usersRepository.findAll().stream()
                .map(user -> new UserList(
                        user.getId(),
                        user.getUsername(),
                        user.getRole().name()
                ))
                .toList();
    }

    public void updateUser(int id, Users updatedUser) {
        Users existingUser = usersRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Users sameUsernameUser = usersRepository.findByUsername(updatedUser.getUsername());
        if (sameUsernameUser != null && sameUsernameUser.getId() != id) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already in use");
        }

        existingUser.setUsername(updatedUser.getUsername());
        existingUser.setRole(updatedUser.getRole());

        if (!bCryptPasswordEncoder.matches(updatedUser.getPassword(), existingUser.getPassword())) {
            existingUser.setPassword(bCryptPasswordEncoder.encode(updatedUser.getPassword()));
        }

        usersRepository.save(existingUser);
    }

    public void deleteUserById(int id) {
        if (!usersRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        usersRepository.deleteById(id);
    }
}
