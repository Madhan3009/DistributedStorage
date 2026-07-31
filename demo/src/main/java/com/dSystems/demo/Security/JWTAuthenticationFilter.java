package com.dSystems.demo.Security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * THE SECURITY CHECKPOINT FILTER.
 * 
 * Think of this class as a "security checkpoint" or "metal detector" that intercepts every single
 * incoming web request before it reaches our website's pages or database.
 * 
 * It looks for a specific HTTP header named "Authorization". If it finds a token, it checks
 * if the token is valid. If it is, it marks the request as "Approved" so the rest of the application
 * knows who is calling and allows them in.
 * 
 * Annotations:
 * - @Component: Registers this class as a reusable security tool in our app.
 * - @Autowired: Tells Spring to automatically find and plug in the required helper tools (JWTHelper & UserDetailsService).
 */
@Component
public class JWTAuthenticationFilter extends OncePerRequestFilter {
    
    // Logger: A tool to print status messages to the console (for debugging).
    private Logger logger = LoggerFactory.getLogger(OncePerRequestFilter.class);
    
    @Autowired
    private JWTHelper jwtHelper;

    @Autowired
    private UserDetailsService userDetailsService;

    /**
     * The core checkpoint filter method.
     * Every web request flows through this method exactly once.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        // Step 1: Look at the headers of the incoming request for "Authorization".
        // Example header value: "Bearer eyJhbGciOi..."
        String requestHeader = request.getHeader("Authorization");
        logger.info(" Header :  {}", requestHeader);
        String username = null;
        String token = null;

        // Step 2: Check if the header exists and starts with the word "Bearer " (which is the standard format).
        if (requestHeader != null && requestHeader.startsWith("Bearer ")) {
            // Trim off the first 7 characters ("Bearer ") to isolate the actual ticket/token string.
            token = requestHeader.substring(7);
            try {
                // Read the username from the token.
                username = this.jwtHelper.getUsernameFromToken(token);
            } catch (IllegalArgumentException e) {
                logger.info("Illegal Argument while fetching the username !!");
                e.printStackTrace();
            } catch (ExpiredJwtException e) {
                logger.info("Given jwt token is expired !!");
                e.printStackTrace();
            } catch (MalformedJwtException e) {
                logger.info("Some change has been done in the token !! Invalid Token");
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            logger.info("Invalid Header Value !! (Or missing Authorization header)");
        }

        // Step 3: If we extracted a username, and Spring Security doesn't already have an active login session for them:
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            // Look up the user's details in the database to verify their existence.
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
            
            // Double check that the token is valid (belongs to this user and isn't expired).
            Boolean validateToken = this.jwtHelper.validateToken(token, userDetails);
            
            if (validateToken) {
                // Set the user as authenticated in Spring Security's context.
                // Think of this as giving the request a VIP pass so all subsequent controllers/guards know they are logged in.
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                logger.info("Validation fails !!");
            }
        }
        
        // Step 4: Let the request pass through to the next gate or controller.
        filterChain.doFilter(request, response);
    }
}
