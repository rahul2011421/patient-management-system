package com.pm.authservice.Service;

import com.pm.authservice.model.User;
import com.pm.authservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * Constructor to inject the UserRepository dependency.
     *
     * @param userRepository the repository to interact with the User data in the database
     */
    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    /**
     * Finds a user by email.
     *
     * @param email the email of the user to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    public Optional<User> findByEmail(String email){
        return userRepository.findByEmail(email);
    }
}
