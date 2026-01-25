package com.pm.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration // Marks this class as a source of bean definitions for the Spring context
public class SecurityConfig {

    // Define a bean for SecurityFilterChain to configure HTTP security
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        // Configures HTTP security
        // Allows all requests without authentication or authorization (i.e., public access)
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                // Disable CSRF protection (not recommended for production unless using stateless authentication)
                .csrf(AbstractHttpConfigurer::disable);

        // Build and return the security filter chain
        return http.build();
    }

    // Define a bean for PasswordEncoder. It provides an encoder for securely hashing passwords
    @Bean
    public PasswordEncoder passwordEncoder(){
        // Return a BCryptPasswordEncoder, which is a widely used hashing algorithm
        return new BCryptPasswordEncoder();
    }
}
