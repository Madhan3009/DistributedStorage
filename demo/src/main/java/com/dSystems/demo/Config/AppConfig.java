package com.dSystems.demo.Config;

import com.dSystems.demo.Security.CustomUserDetailsService;
import com.dSystems.demo.Security.JWTAuthenticationFilter;
import com.dSystems.demo.Security.JWTAthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

/**
 * THE SECURITY CONFIGURATION MANUAL.
 * 
 * Think of this class as the "security setup manual" for our app. It defines who is allowed
 * to visit which parts of our website/API, how we verify users, and how we protect passwords.
 * 
 * Annotations:
 * - @Configuration: Tells Spring that this file contains settings and recipes (called "Beans")
 *   for building various tools the application needs.
 * - @EnableMethodSecurity: Allows us to lock/unlock specific actions later using annotations.
 */
@Configuration
@EnableMethodSecurity
public class AppConfig {

    // These are security helpers we need to enforce our rules:
    // 1. CustomUserDetailsService: Helps find user accounts in our database.
    // 2. JWTAuthenticationFilter: The bouncer checking for "admission tickets" (JWT tokens) on every request.
    // 3. JWTAthenticationEntryPoint: The bouncer that rejects people who don't have valid tickets.
    private final CustomUserDetailsService userDetailsService;
    private final JWTAuthenticationFilter jwtAuthenticationFilter;
    private final JWTAthenticationEntryPoint authenticationEntryPoint;

    /**
     * Constructor - This is where the app passes the needed security helpers to this configuration manual.
     */
    public AppConfig(CustomUserDetailsService userDetailsService,
                     JWTAuthenticationFilter jwtAuthenticationFilter,
                     JWTAthenticationEntryPoint authenticationEntryPoint) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * The Security Gate/Filter Chain.
     * This defines the checklist that every incoming web request must pass through.
     * 
     * @Bean: Tells Spring to create this security filter and use it globally.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF: Typically disabled for APIs that use tokens, as tokens prevent this type of attack.
                .csrf(AbstractHttpConfigurer::disable)
                
                // Set the entry point: If someone is not logged in and tries to access a restricted page,
                // this tells the system how to respond (e.g. return a 401 Unauthorized error code).
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(authenticationEntryPoint))
                
                // Stateless Sessions: The server will not remember users between visits.
                // Every single request must prove who the sender is by carrying a token (like showing a ticket every time you enter a room).
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Route Permissions: Here we define who can access which web addresses (URLs).
                .authorizeHttpRequests(auth -> auth
                        // Allow anyone to access authentication APIs (login, register)
                        .requestMatchers("/auth/**").permitAll()
                        // Allow anyone to access node management APIs (servers registering themselves)
                        .requestMatchers("/nodes/**").permitAll()
                        // Allow anyone to access internal cluster APIs (nodes talking to nodes)
                        .requestMatchers("/internal/**").permitAll()
                        // Allow anyone to see the dashboard / homepage
                        .requestMatchers("/dashboard/**", "/dashboard", "/", "/error").permitAll()
                        // Allow anyone to access system health metrics
                        .requestMatchers("/actuator/**").permitAll()
                        // All other URLs not listed above require the user to be logged in!
                        .anyRequest().authenticated())
                
                // Tell the system how to verify username/password credentials.
                .authenticationProvider(authenticationProvider())
                
                // Add our custom JWT token bouncer filter *before* the default username/password filter is checked.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The Authenticator Provider.
     * This is the mechanism that looks up a user's details and checks their password using our encoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        // Teach the provider to use our secure password encoder when verifying passwords.
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * The Password Scrambler (Encoder).
     * This ensures we NEVER save plain-text passwords in the database.
     * It uses BCrypt, a strong mathematical formula that turn "mypassword123" into an unreadable string like "$2a$10$xyz...".
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The Authentication Manager.
     * This is the central manager coordinate user logins. Other classes call this to say "please log this user in".
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

