package com.dSystems.demo.Security;

import com.dSystems.demo.Model.AppUser;
import com.dSystems.demo.Repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * THE USER-LOOKUP ASSISTANT.
 * 
 * Think of this class as a specialized assistant that Spring Security calls whenever it needs to verify a user.
 * Its only job is to go to the database, search for a user by their username or email, and package
 * their details (like their password and permissions) into a format that Spring Security can understand.
 * 
 * Annotations:
 * - @Service: Tells Spring that this is a service class containing core business logic.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    // The database helper we use to search for user accounts.
    private final AppUserRepository appUserRepository;

    /**
     * Constructor - Receives the database helper from Spring.
     */
    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Searches for a user in the database and converts them to Spring's internal security format.
     * 
     * @param usernameOrEmail The username or email entered in the login form.
     * @return UserDetails A standard Spring container holding the username, hashed password, and permissions.
     * @throws UsernameNotFoundException Triggered if no user is found with that username or email.
     */
    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // Step 1: Look in our database. If not found, throw an error immediately to halt login.
        AppUser user = appUserRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));

        // Step 2: Build and return Spring's standard UserDetails container with:
        // - username
        // - password (hashed)
        // - authorities (their roles/permissions, like "ROLE_USER")
        // - disabled state (toggles whether the account is suspended)
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole())))
                .disabled(!user.isEnabled())
                .build();
    }
}
