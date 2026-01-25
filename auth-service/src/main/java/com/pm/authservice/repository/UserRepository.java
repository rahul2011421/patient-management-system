package com.pm.authservice.repository;

import com.pm.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Repository interface to perform CRUD operations for User entity
public interface UserRepository extends JpaRepository<User, UUID> {

    // Custom query method to find a user by their email address
    // This method returns an Optional<User> to handle cases where the user might not exist
    Optional<User> findByEmail(String email);
}
