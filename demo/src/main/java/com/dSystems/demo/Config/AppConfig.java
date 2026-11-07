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
 * Spring Security configuration: route authorization, stateless JWT authentication, and
 * password encoding. Also exposes the shared {@link RestTemplate} bean.
 */
@Configuration
@EnableMethodSecurity
public class AppConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JWTAuthenticationFilter jwtAuthenticationFilter;
    private final JWTAthenticationEntryPoint authenticationEntryPoint;

    public AppConfig(CustomUserDetailsService userDetailsService,
                     JWTAuthenticationFilter jwtAuthenticationFilter,
                     JWTAthenticationEntryPoint authenticationEntryPoint) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * Defines the security filter chain applied to all incoming requests.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is unnecessary: authentication uses bearer tokens, not session cookies.
                .csrf(AbstractHttpConfigurer::disable)
                
                // Returns 401 Unauthorized for unauthenticated requests to protected routes.
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(authenticationEntryPoint))
                
                // No server-side sessions; each request must carry its own JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Route authorization rules.
                .authorizeHttpRequests(auth -> auth
                        // Login and registration.
                        .requestMatchers("/auth/**").permitAll()
                        // Node registration, heartbeats, and cluster administration.
                        .requestMatchers("/nodes/**").permitAll()
                        // Chunk storage endpoints used for coordinator-to-node traffic.
                        .requestMatchers("/internal/**").permitAll()
                        // Dashboard static assets and the root redirect.
                        .requestMatchers("/dashboard/**", "/dashboard", "/", "/error").permitAll()
                        // Actuator health and metrics.
                        .requestMatchers("/actuator/**").permitAll()
                        // All remaining routes require authentication.
                        .anyRequest().authenticated())
                
                // Username/password verification used by the login flow.
                .authenticationProvider(authenticationProvider())
                
                // Authenticate bearer tokens before the username/password filter runs.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Authenticates credentials against stored users using the configured password encoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCrypt encoder used to hash passwords before storage and to verify them at login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the {@link AuthenticationManager} used by the login endpoint.
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

