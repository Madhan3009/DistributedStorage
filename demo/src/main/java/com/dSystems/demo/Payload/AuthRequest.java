package com.dSystems.demo.Payload;

import jakarta.validation.constraints.NotBlank;

/**
 * THE LOGIN FORM / SIGN-IN REQUEST PAYLOAD.
 * 
 * Think of this class as a digital "Login Form". When a user tries to sign in on the website,
 * their browser packs their credentials (username and password) into this container and sends it
 * over the internet.
 * 
 * This is a "Data Transfer Object" (DTO) - it doesn't store permanent data, it just holds information
 * in transit like a shipping envelope.
 */
public class AuthRequest {

    // The username typed by the user.
    // - @NotBlank: A validation rule. If the user leaves this blank or types only spaces,
    //   the server will automatically reject the form with an error before running any other code.
    @NotBlank
    private String username;

    // The password typed by the user.
    // - @NotBlank: This is also required and cannot be left empty.
    @NotBlank
    private String password;

    // --- GETTERS AND SETTERS ---
    // Controls to write and read the form fields.

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
