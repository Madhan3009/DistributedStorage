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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTests {

    @LocalServerPort
    private int port;

    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        String username = "futureuser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String registerPayload = """
                {
                  "username": "%s",
                  "email": "%s@example.com",
                  "password": "secret123"
                }
                """.formatted(username, username);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest registerRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(registerPayload))
                .build();
        HttpResponse<String> registerResponse = client.send(registerRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.CREATED.value(), registerResponse.statusCode());

        String loginPayload = """
                {
                  "username": "%s",
                  "password": "secret123"
                }
                """.formatted(username);

        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), loginResponse.statusCode());
        assertFalse(loginResponse.body().isBlank());
        assertFalse(loginResponse.body().contains("\"token\":\"\""));
        assertFalse(loginResponse.body().contains("\"username\":\"\""));

        String token = loginResponse.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        assertFalse(token.isBlank());

        HttpRequest meRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> meResponse = client.send(meRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), meResponse.statusCode());
        assertEquals(username, meResponse.body());
    }
}
