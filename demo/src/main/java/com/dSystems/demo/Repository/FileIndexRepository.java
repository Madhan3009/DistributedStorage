package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.FileIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

/**
 * Persistence operations for {@link FileIndex}.
 */
public interface FileIndexRepository extends JpaRepository<FileIndex, Long> {
    
    /**
     * Finds a file record by its unique identifier.
     */
    Optional<FileIndex> findByFileId(String fileId);

    /**
     * Finds all files owned by a user.
     */
    List<FileIndex> findByOwnerUsername(String ownerUsername);

    /**
     * @return true if a file record exists for the identifier
     */
    boolean existsByFileId(String fileId);
}
