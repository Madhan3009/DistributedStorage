package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * THE USER DATABASE CLERK / REPOSITORY.
 * 
 * Think of a "Repository" as a specialized database assistant/clerk. 
 * In Java, an "interface" is just a job description. We only define the rules here,
 * and Spring Boot automatically writes the actual database code behind the scenes!
 * 
 * - JpaRepository<AppUser, Long>: This tells Spring that this clerk knows how to perform basic
 *   database actions (like Save, Delete, Find, Update) for the "AppUser" data model, using "Long" IDs.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    
    /**
     * Finds a user in the database by either their username OR their email.
     * 
     * Spring Boot looks at this method's name and automatically writes a SQL database query:
     * "SELECT * FROM app_users WHERE username = ? OR email = ?"
     * 
     * @return An "Optional" box containing the user if found, or an empty box if not found.
     *         This prevents the program from crashing if a user doesn't exist.
     */
    Optional<AppUser> findByUsernameOrEmail(String username, String email);

    /**
     * Checks if a username already exists in the database.
     * 
     * Spring automatically generates: "SELECT COUNT(*) FROM app_users WHERE username = ?"
     * @return true if someone is already using that username, false otherwise.
     */
    boolean existsByUsername(String username);

    /**
     * Checks if an email already exists in the database.
     * 
     * @return true if that email is already registered, false otherwise.
     */
    boolean existsByEmail(String email);
}
