package com.pm.authservice.Service;

import com.pm.authservice.dto.LoginRequestDTO;
import com.pm.authservice.model.User;
import com.pm.authservice.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Constructor that initializes AuthService with required dependencies:
     * - UserService: To fetch user details from the database
     * - PasswordEncoder: To compare hashed passwords
     * - JwtUtil: To generate and validate JWT tokens
     */
    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil){
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Authenticates the user by checking email and password.
     * Generates and returns a JWT token if authentication is successful.
     *
     * @param loginRequestDTO contains email and password
     * @return Optional containing JWT token if successful, empty Optional if authentication fails
     */
    public Optional<String> authenticate(LoginRequestDTO loginRequestDTO){
        // Find user by email and check if the password matches the stored hash
        return userService.findByEmail(loginRequestDTO.getEmail())
                .filter(u -> passwordEncoder.matches(loginRequestDTO.getPassword(), u.getPassword()))
                .map(u -> jwtUtil.generateToken(u.getEmail(), u.getRole())); // Generate token if valid
    }

    /**
     * Validates a JWT token by checking its integrity and expiry.
     *
     * @param token the JWT token to validate
     * @return true if token is valid, false if invalid or expired
     */
    public boolean validateToken(String token){
        try {
            jwtUtil.validateToken(token);  // Validate the token using JwtUtil
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
