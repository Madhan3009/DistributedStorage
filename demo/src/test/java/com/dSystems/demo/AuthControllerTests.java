package com.dSystems.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * THE AUTHENTICATION FLOW TEST (THE ROBOT INSPECTOR).
 * 
 * Think of this class as an automated robot that tests our authentication system. 
 * Instead of a human manually opening a browser and typing details, this robot:
 * 1. Boots up the entire Spring Boot website programmatically.
 * 2. Simulates a new user registering an account.
 * 3. Simulates that user logging in and receiving a JWT token ticket.
 * 4. Simulates the user making a request to a protected/locked page using that ticket,
 *    and verifies that the website lets them in.
 * 
 * Annotations:
 * - @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT): Boots up the entire
 *   application server on a random unused network port (so it doesn't conflict with any other running servers).
 * - @ActiveProfiles("test"): Tells Spring to use test-specific configuration settings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTests {

    // Spring automatically fills this variable with the random port number it chose for the test.
    @LocalServerPort
    private int port;

    /**
     * Test Case: Register, log in, and visit a protected page.
     * 
     * - @Test: Marks this method as an automated test case that will run during build checks.
     */
    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        // Step 1: Create a completely unique username (e.g., "futureuser_1a2b3c4d") so we don't
        // clash with any existing users in the test database.
        String username = "futureuser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String registerPayload = """
                {
                  "username": "%s",
                  "email": "%s@example.com",
                  "password": "secret123"
                }
                """.formatted(username, username);

        // Java's built-in HttpClient is like a programmatic web browser.
        HttpClient client = HttpClient.newHttpClient();

        // Send a POST request to "/auth/register" with the JSON user details
        HttpRequest registerRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(registerPayload))
                .build();
        HttpResponse<String> registerResponse = client.send(registerRequest, HttpResponse.BodyHandlers.ofString());
        
        // Assert: Verify that the registration response has a status code of 201 (Created)
        assertEquals(HttpStatus.CREATED.value(), registerResponse.statusCode());

        // Step 2: Formulate the login parameters
        String loginPayload = """
                {
                  "username": "%s",
                  "password": "secret123"
                }
                """.formatted(username);

        // Send a POST request to "/auth/login"
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        
        // Assert: Verify login is successful (HTTP 200 OK) and returns some data
        assertEquals(HttpStatus.OK.value(), loginResponse.statusCode());
        assertFalse(loginResponse.body().isBlank());
        assertFalse(loginResponse.body().contains("\"token\":\"\""));
        assertFalse(loginResponse.body().contains("\"username\":\"\""));

        // Extract the JWT token string from the JSON response using a text pattern replacement
        String token = loginResponse.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertFalse(token.isBlank());

        // Step 3: Visit the locked endpoint "/api/me"
        // Notice we attach the ticket in the "Authorization" header as "Bearer <token>"
        HttpRequest meRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> meResponse = client.send(meRequest, HttpResponse.BodyHandlers.ofString());
        
        // Assert: Verify the locked page accepts the token (HTTP 200 OK) and knows our name
        assertEquals(HttpStatus.OK.value(), meResponse.statusCode());
        assertEquals(username, meResponse.body());
    }
}
