package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence operations for {@link AppUser}.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    
    /**
     * Finds a user whose username or email matches the corresponding argument.
     *
     * @return the matching user, or empty if none exists
     */
    Optional<AppUser> findByUsernameOrEmail(String username, String email);

    /**
     * @return true if the username is already registered
     */
    boolean existsByUsername(String username);

    /**
     * @return true if the email address is already registered
     */
    boolean existsByEmail(String email);
}
