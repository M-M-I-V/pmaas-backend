package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.service.UsersService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    UsersService usersService;

    public AuthController(UsersService usersService) {
        this.usersService = usersService;
    }

    @PostMapping("/login")
    public String login(@RequestBody Users user) {
        return usersService.verifyUser(user);
    }

}
