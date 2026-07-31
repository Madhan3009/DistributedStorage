package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.FileIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

/**
 * THE FILE CATALOG DATABASE CLERK / REPOSITORY.
 * 
 * This database assistant is in charge of writing, reading, and updating the catalog records (FileIndex) 
 * for files uploaded to our cluster.
 */
public interface FileIndexRepository extends JpaRepository<FileIndex, Long> {
    
    /**
     * Looks up a file record by its unique system ID (barcode).
     * 
     * @return An Optional box containing the file's details if found.
     */
    Optional<FileIndex> findByFileId(String fileId);

    /**
     * Retrieves a list of all files that belong to a specific user.
     * 
     * Spring automatically generates: "SELECT * FROM file_indices WHERE owner_username = ?"
     * @return A list of files owned by the user.
     */
    List<FileIndex> findByOwnerUsername(String ownerUsername);

    /**
     * Checks if a file ID is already registered in our catalog.
     */
    boolean existsByFileId(String fileId);
}
