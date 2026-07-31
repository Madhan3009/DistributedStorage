package com.dSystems.demo.Controller;

import com.dSystems.demo.Model.AppUser;
import com.dSystems.demo.Payload.AuthRequest;
import com.dSystems.demo.Payload.AuthResponse;
import com.dSystems.demo.Payload.RegisterRequest;
import com.dSystems.demo.Repository.AppUserRepository;
import com.dSystems.demo.Security.JWTHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * THE AUTHENTICATION DESK / RECEPTIONIST.
 * 
 * Think of this class as the entrance gate reception desk. 
 * It provides two services (endpoints) to visitors:
 * 1. Register: Create a new user profile, scramble (hash) their password, and save it in the database.
 * 2. Login: Verify their password, and if correct, issue a signed JWT admission ticket.
 * 
 * Annotations:
 * - @RestController: Tells Spring Boot that this class is a web endpoint handler that outputs JSON data.
 * - @RequestMapping("/auth"): Tells Spring that all web addresses in this file start with "/auth".
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    // Helper managers injected by Spring:
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTHelper jwtHelper;

    /**
     * Constructor - Receives the authentication, database, hashing, and token helpers.
     */
    public AuthController(AuthenticationManager authenticationManager,
                          AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          JWTHelper jwtHelper) {
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtHelper = jwtHelper;
    }

    /**
     * SERVICE: Register a new user account.
     * Web URL: POST http://localhost:8080/auth/register
     * 
     * - @Valid: Automatically checks validation constraints on the request form (e.g. valid email, min size).
     * - @RequestBody: Grabs the incoming JSON payload from the request body.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Step 1: Ensure the requested username is not already taken
        if (appUserRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }

        // Step 2: Ensure the email is not already registered
        if (appUserRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists");
        }

        // Step 3: Create a new user database record
        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        
        // CRITICAL: Scramble (hash) the password using BCrypt before storing it.
        // We never store passwords in readable plain text!
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        // Save the new user record in our database
        appUserRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }

    /**
     * SERVICE: Log in to an existing account.
     * Web URL: POST http://localhost:8080/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            // Step 1: Tell Spring Security's authentication manager to check if the username/password match.
            // Under the hood, this loads the user using CustomUserDetailsService and verifies the BCrypt hashes match.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            
            // Step 2: Retrieve the authenticated user details
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            
            // Step 3: Print a signed JWT admission ticket for this user
            String token = jwtHelper.generateToken(userDetails);
            
            // Step 4: Hand back the ticket along with their username in the HTTP response
            return ResponseEntity.ok(new AuthResponse(token, userDetails.getUsername()));
        } catch (BadCredentialsException ex) {
            // If username or password check fails, return status 401 (Unauthorized)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }
}
