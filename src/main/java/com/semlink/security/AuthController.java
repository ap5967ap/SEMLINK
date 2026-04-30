package com.semlink.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        // Hardcoded dummy users for demo
        String role = null;
        if ("admin".equals(username) && "admin123".equals(password)) {
            role = "ROLE_ADMIN";
        } else if ("analyst".equals(username) && "analyst123".equals(password)) {
            role = "ROLE_ANALYST";
        } else if ("viewer".equals(username) && "viewer123".equals(password)) {
            role = "ROLE_READONLY";
        }

        if (role != null) {
            String token = jwtUtil.generateToken(username, role);
            return ResponseEntity.ok(Map.of("token", token, "role", role));
        }

        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
}
