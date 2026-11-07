package com.dSystems.demo.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnostic endpoint for verifying that a JWT is accepted.
 */
@RestController
@RequestMapping("/api")
public class TestController {

    /**
     * {@code GET /api/me}
     *
     * @param authentication the authenticated caller
     * @return the caller's username
     */
    @GetMapping("/me")
    public String currentUser(Authentication authentication) {
        return authentication.getName();
    }
}
