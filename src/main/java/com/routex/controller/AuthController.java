package com.routex.controller;

import com.routex.model.User;
import com.routex.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Presentation Layer — Auth REST endpoints.
 * POST /api/auth/register  — create new account
 * POST /api/auth/login     — authenticate and return user info
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authManager;

    // ---- DTOs ----

    @Data
    public static class RegisterRequest {
        @NotBlank  private String name;
        @Email     private String email;
        @Size(min=6) private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank private String email;
        @NotBlank private String password;
    }

    // ---- Endpoints ----

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            User user = userService.register(req.getName(), req.getEmail(), req.getPassword());
            return ResponseEntity.ok(userToMap(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
            User user = userService.findByEmail(req.getEmail()).orElseThrow();
            return ResponseEntity.ok(userToMap(user));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        User user = userService.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.ok(userToMap(user));
    }

    private Map<String, Object> userToMap(User u) {
        return Map.of("id", u.getId(), "name", u.getName(),
                "email", u.getEmail(), "role", u.getRole());
    }
}
