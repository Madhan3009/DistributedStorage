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
 * Handles user registration and login. Successful logins return a signed JWT.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTHelper jwtHelper;

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
     * Registers a new user account.
     * {@code POST /auth/register}
     *
     * @return 201 on success, or 409 if the username or email is already in use
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Reject duplicate usernames.
        if (appUserRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }

        // Reject duplicate email addresses.
        if (appUserRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists");
        }

        // Create the user record.
        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        
        // Store only the BCrypt hash of the password.
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        appUserRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }

    /**
     * Authenticates a user and issues a JWT.
     * {@code POST /auth/login}
     *
     * @return 200 with the token and username, or 401 if the credentials are invalid
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        try {
            // Delegates to CustomUserDetailsService and verifies the password against its BCrypt hash.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            
            String token = jwtHelper.generateToken(userDetails);
            
            return ResponseEntity.ok(new AuthResponse(token, userDetails.getUsername()));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }
}
