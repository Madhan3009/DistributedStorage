package com.dSystems.demo.Security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * THE ACCESS-DENIED BOUNCER.
 * 
 * Think of this class as a security bouncer stationed at the door of locked rooms. 
 * If a user tries to open a locked door (an API that requires logging in) but fails to show
 * a valid ticket (JWT token), this class is automatically called to kick them out and hand them
 * a rejection slip.
 * 
 * Annotations:
 * - @Component: Registers this class as a managed tool in our Spring Boot application.
 */
@Component
public class JWTAthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * This method is triggered the exact moment a user is caught trying to access a restricted
     * area without a valid login token.
     * 
     * @param request The incoming request from the user's browser.
     * @param response The reply we are going to send back to the user's browser.
     * @param authException The specific security error details (why they were rejected).
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        // Step 1: Set the web status code to 401 (Unauthorized), which is the official internet code for "You must log in".
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        // Step 2: Write a clear plain text rejection message directly back to the visitor's screen.
        PrintWriter writer = response.getWriter();
        writer.println("Access Denied !! " + authException.getMessage());
    }
}