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
 * Responds with 401 Unauthorized when an unauthenticated request reaches a protected route.
 */
@Component
public class JWTAthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Writes the 401 response.
     *
     * @param request the rejected request
     * @param response the response to write
     * @param authException the reason authentication failed
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        PrintWriter writer = response.getWriter();
        writer.println("Access Denied !! " + authException.getMessage());
    }
}