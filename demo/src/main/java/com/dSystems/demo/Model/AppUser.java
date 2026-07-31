package com.dSystems.demo.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * THE USER ACCOUNT DATA MODEL.
 * 
 * Think of this class as a blueprint/form for a user's account details. It tells the application
 * exactly what information we store about each registered user (ID, username, email, password, etc.).
 * 
 * Annotations:
 * - @Entity: Tells the database manager (Hibernate/JPA) that this class represents a database table.
 * - @Table(name = "app_users"): Specifies that this table should be named "app_users" in the database.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    // The unique ID number for the user (like a social security number or library card number).
    // - @Id: Marks this variable as the unique identifier (Primary Key) in the database.
    // - @GeneratedValue: Tells the database to automatically increase this number for each new user (+1).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user's custom login name.
    // - @Column: Maps this to a database column.
    // - nullable = false: Every user MUST have a username (cannot be empty).
    // - unique = true: No two users can have the same username.
    @Column(nullable = false, unique = true)
    private String username;

    // The user's email address.
    // - unique = true: No two users can register with the same email.
    @Column(nullable = false, unique = true)
    private String email;

    // The user's password (this will be the scrambled/hashed version for safety).
    @Column(nullable = false)
    private String password;

    // The role/permission level of the user (e.g. "ROLE_USER" or "ROLE_ADMIN").
    @Column(nullable = false)
    private String role = "ROLE_USER";

    // Whether the account is active/enabled (true) or suspended/disabled (false).
    @Column(nullable = false)
    private boolean enabled = true;

    // --- GETTERS AND SETTERS ---
    // Controls for accessing or updating private user information.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
