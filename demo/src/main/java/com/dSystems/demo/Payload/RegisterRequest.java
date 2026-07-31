package com.dSystems.demo.Payload;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * THE REGISTRATION / SIGN-UP FORM PAYLOAD.
 * 
 * Think of this class as a digital "Sign-Up Form". When a new user wants to create an account,
 * they fill in their desired username, email, and password. Their browser packs these details
 * here and sends it to the server.
 */
public class RegisterRequest {

    // The desired username.
    // - @NotBlank: Cannot be empty.
    // - @Size(min = 3, max = 50): Must be between 3 and 50 characters long (to prevent very short or spammy names).
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    // The desired email address.
    // - @NotBlank: Cannot be empty.
    // - @Email: Must look like a real email address (e.g., must contain an '@' and a domain name like '.com').
    @NotBlank
    @Email
    private String email;

    // The chosen password.
    // - @NotBlank: Cannot be empty.
    // - @Size(min = 6, max = 100): Must be at least 6 characters long to ensure basic password strength/security.
    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    // --- GETTERS AND SETTERS ---
    // Methods to write and read the registration form fields.

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
