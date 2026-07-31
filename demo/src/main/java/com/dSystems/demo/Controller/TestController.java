package com.dSystems.demo.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * THE WHOAMI / TEST CONTROLLER.
 * 
 * Think of this class as a quick check-in window. 
 * If a user wants to verify if their login token is valid, they can send a request here.
 * It reads their token and simply replies back with their username.
 * 
 * Annotations:
 * - @RestController: Marks this as a REST controller.
 * - @RequestMapping("/api"): Prepends "/api" to paths in this file.
 */
@RestController
@RequestMapping("/api")
public class TestController {

    /**
     * SERVICE: Get the currently logged-in user's name.
     * Web URL: GET http://localhost:8080/api/me
     * 
     * @param authentication Injected by Spring Security, containing token details.
     * @return The username of the authenticated caller.
     */
    @GetMapping("/me")
    public String currentUser(Authentication authentication) {
        return authentication.getName();
    }
}
