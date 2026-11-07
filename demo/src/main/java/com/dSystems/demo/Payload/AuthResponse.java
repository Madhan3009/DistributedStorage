package com.dSystems.demo.Payload;

/**
 * Successful login response containing the issued JWT and the authenticated username.
 */
public class AuthResponse {

    // Signed JWT, sent by the client as a bearer token on subsequent requests.
    private final String token;
    
    private final String username;

    public AuthResponse(String token, String username) {
        this.token = token;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }
}
