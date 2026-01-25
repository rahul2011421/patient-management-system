package com.pm.authservice.controller;

import com.pm.authservice.Service.AuthService;
import com.pm.authservice.dto.LoginRequestDTO;
import com.pm.authservice.dto.LoginResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
public class AuthController {

    private final AuthService authService;

    /**
     * Constructor to inject AuthService, responsible for authentication and token management.
     *
     * @param authService the service that handles user authentication and token generation
     */
    public AuthController(AuthService authService){
        this.authService = authService;
    }

    /**
     * Endpoint to authenticate user login. Validates the credentials and generates a JWT token if valid.
     *
     * @param loginRequestDTO contains the username and password for authentication
     * @return 200 OK with the JWT token if authentication succeeds, or 401 Unauthorized if authentication fails
     */
    @Operation(summary = "Generate token on user login")  // Swagger annotation for API documentation
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequestDTO){
        // Attempt to authenticate the user with the provided login credentials (email/password)
        Optional<String> tokenOptional = authService.authenticate(loginRequestDTO);

        // If authentication fails (no token generated), return a 401 Unauthorized response
        if(tokenOptional.isEmpty()){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // If authentication is successful, return the generated token in the response body
        String token = tokenOptional.get();
        return ResponseEntity.ok(new LoginResponseDTO(token));  // 200 OK with the token in the response body
    }

    /**
     * Endpoint to validate a JWT token provided in the Authorization header.
     * Checks whether the token is valid and has not expired.
     *
     * @param authHeader the Authorization header containing the token (e.g., "Bearer <token>")
     * @return 200 OK if the token is valid, or 401 Unauthorized if the token is invalid or expired
     */
    @Operation(summary = "Validate Token")  // Swagger annotation for API documentation
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String authHeader){
        // Ensure the Authorization header is present and correctly formatted (starts with "Bearer ")
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Extract the token by removing the "Bearer " prefix and validate it
        boolean isValid = authService.validateToken(authHeader.substring(7));

        // Return 200 OK if the token is valid, or 401 Unauthorized if invalid
        return isValid ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
