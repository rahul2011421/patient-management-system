package com.pm.authservice.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key secretKey;

    /**
     * Constructor to initialize the secret key for JWT signing and validation.
     * This key is base64-decoded and converted into a SecretKey instance for HMAC-SHA signing.
     *
     * @param secret the base64-encoded secret key for signing and verifying JWTs
     */
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.UTF_8));  // Decode the base64 secret
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);  // Generate the HMAC key from decoded bytes
    }

    /**
     * Generates a JWT token with the provided email and role.
     * The token includes claims like email and role and is signed using the secret key.
     * The token expires in 10 hours, which is adjustable.
     *
     * @param email the email address of the user
     * @param role the role of the user (e.g., "admin", "user")
     * @return a JWT token as a string
     */
    public String generateToken(String email, String role){
        return Jwts.builder()
                .subject(email)  // Set the email as the subject claim in the JWT
                .claim("role", role)  // Set the user's role as a claim in the JWT
                .issuedAt(new Date())  // Set the current timestamp as the issue date
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))  // Set token expiration to 10 hours from now
                .signWith(secretKey)  // Sign the JWT using the secret key
                .compact();  // Generate and return the JWT string
    }

    /**
     * Validates a JWT token by checking its signature and other claims.
     * Throws JwtException if the token is invalid, expired, or if the signature is incorrect.
     *
     * @param token the JWT token to be validated
     * @throws JwtException if the token is invalid or expired
     */
    public void validateToken(String token){
        try{
            Jwts.parser().verifyWith((SecretKey) secretKey)  // Verify the JWT's signature using the secret key
                    .build()  // Create the parser object
                    .parseSignedClaims(token);  // Parse and validate the token's claims
        } catch (SignatureException e){
            // Thrown if the JWT signature is invalid
            throw new JwtException("Invalid JWT signature");
        } catch (JwtException e) {
            // Thrown if the token is invalid or expired
            throw new JwtException("Invalid JWT");
        }
    }
}
