package com.dSystems.demo.Config;

import com.dSystems.demo.Security.ClusterAuthenticationFilter;
import com.dSystems.demo.Security.CustomUserDetailsService;
import com.dSystems.demo.Security.JWTAuthenticationFilter;
import com.dSystems.demo.Security.JWTAthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * password encoding. Also exposes the {@link RestTemplate} used for cluster traffic.
 */
@Configuration
@EnableMethodSecurity
public class AppConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JWTAuthenticationFilter jwtAuthenticationFilter;
    private final ClusterAuthenticationFilter clusterAuthenticationFilter;
    private final JWTAthenticationEntryPoint authenticationEntryPoint;

    public AppConfig(CustomUserDetailsService userDetailsService,
                     JWTAuthenticationFilter jwtAuthenticationFilter,
                     ClusterAuthenticationFilter clusterAuthenticationFilter,
                     JWTAthenticationEntryPoint authenticationEntryPoint) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.clusterAuthenticationFilter = clusterAuthenticationFilter;
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
                        // Node registration and heartbeats require the cluster token.
                        .requestMatchers(HttpMethod.POST, "/nodes/register", "/nodes/heartbeat").hasRole("CLUSTER")
                        // Chunk storage endpoints require the cluster token.
                        .requestMatchers("/internal/**").hasRole("CLUSTER")
                        // Dashboard static assets and the root redirect.
                        .requestMatchers("/dashboard/**", "/dashboard", "/", "/error").permitAll()
                        // Actuator health and metrics.
                        .requestMatchers("/actuator/**").permitAll()
                        // All remaining routes, including node listing and failure simulation, require a user JWT.
                        .anyRequest().authenticated())
                
                // Username/password verification used by the login flow.
                .authenticationProvider(authenticationProvider())
                
                // Authenticate bearer tokens before the username/password filter runs.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Authenticate cluster tokens on coordinator and storage node endpoints.
                .addFilterBefore(clusterAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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

    /**
     * Client for coordinator and storage node traffic. Sends the cluster token on every request,
     * so it must only be used to call cluster members.
     */
    @Bean
    public RestTemplate restTemplate(@Value("${app.cluster.secret}") String clusterSecret) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(ClusterAuthenticationFilter.HEADER_NAME, clusterSecret);
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}

