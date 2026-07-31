package com.dSystems.demo.Controller;

import com.dSystems.demo.Config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * THE STORAGE NODE'S INTERNAL GATEWAY.
 * 
 * Think of this class as the backend loading dock running on each individual storage server (worker node).
 * Only the central coordinator server talks to these endpoints; regular users never call them directly.
 * 
 * It handles the low-level physical file operations on the server's local hard drive:
 * 1. Store: Receive a binary chunk from the coordinator and write it to a local folder.
 * 2. Fetch: Locate a stored chunk and stream its bytes back to the coordinator.
 * 3. Delete: Wipe out all chunk files associated with a deleted file ID.
 * 4. Health: A simple "Are you awake?" ping endpoint.
 * 
 * Annotations:
 * - @RestController: Marks this as a REST endpoint controller.
 * - @RequestMapping("/internal"): Prepends "/internal" to all paths in this file.
 */
@RestController
@RequestMapping("/internal")
public class InternalNodeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalNodeController.class);

    // The root directory on this storage node's hard drive where file chunks are kept
    private final Path storagePath;

    /**
     * Constructor - Configures and creates the storage directory path on startup.
     */
    public InternalNodeController(StorageProperties storageProperties) throws IOException {
        this.storagePath = Path.of(storageProperties.getTempDir()).toAbsolutePath().normalize();
        Files.createDirectories(this.storagePath);
    }

    /**
     * SERVICE: Store a chunk on disk.
     * Web URL: POST http://localhost:8081/internal/store
     * 
     * @param fileId The unique ID of the file.
     * @param chunkNumber The index of this chunk.
     * @param chunkFile The raw binary file chunk.
     */
    @PostMapping("/store")
    public ResponseEntity<String> storeChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("file") MultipartFile chunkFile
    ) {
        try {
            // Resolve the folder path for this file: /storage_dir/{fileId}
            Path fileDir = storagePath.resolve(fileId).normalize();
            
            // SECURITY CHECK (Path Traversal Protection):
            // Prevents malicious paths like "../../etc" that try to escape the storage root.
            if (!fileDir.startsWith(storagePath)) {
                return ResponseEntity.badRequest().body("Invalid file path");
            }
            Files.createDirectories(fileDir);

            // Path to save the chunk file: /storage_dir/{fileId}/chunk-{chunkNumber}.part
            Path targetFile = fileDir.resolve("chunk-" + chunkNumber + ".part");
            
            // Copy the incoming binary stream directly to the target file path, replacing any old copy
            Files.copy(chunkFile.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Successfully stored chunk {} for fileId {}", chunkNumber, fileId);
            return ResponseEntity.ok("Stored");
        } catch (IOException e) {
            LOGGER.error("Failed to store chunk {} for fileId {}: {}", chunkNumber, fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * SERVICE: Fetch a chunk from disk to send back to the coordinator.
     * Web URL: GET http://localhost:8081/internal/fetch
     */
    @GetMapping("/fetch")
    public ResponseEntity<Resource> fetchChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("chunkNumber") int chunkNumber
    ) {
        // Resolve the target chunk path on disk
        Path targetFile = storagePath.resolve(fileId).resolve("chunk-" + chunkNumber + ".part").normalize();
        
        // Ensure path is safe and the file actually exists
        if (!targetFile.startsWith(storagePath) || !Files.exists(targetFile)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Wrap the physical file in a Spring Resource object
        Resource resource = new FileSystemResource(targetFile.toFile());
        
        // Return the file with headers indicating it is a binary stream attachment
        return ResponseEntity.ok()
                 .contentType(MediaType.APPLICATION_OCTET_STREAM)
                 .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chunk-" + chunkNumber + ".part\"")
                 .body(resource);
    }

    /**
     * SERVICE: Delete all chunks of a file.
     * Web URL: DELETE http://localhost:8081/internal/delete
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteChunks(@RequestParam("fileId") String fileId) {
        try {
            Path fileDir = storagePath.resolve(fileId).normalize();
            
            // Safety check & bypass if already deleted
            if (!fileDir.startsWith(storagePath) || !Files.exists(fileDir)) {
                return ResponseEntity.ok("Deleted (directory did not exist)");
            }
            
            // Recursively walk through and delete all chunk files, then delete the empty folder
            try (var paths = Files.walk(fileDir)) {
                paths.sorted(Comparator.reverseOrder()) // Delete files inside first, then parent folder
                     .map(Path::toFile)
                     .forEach(File::delete);
            }
            LOGGER.info("Deleted chunks for fileId {}", fileId);
            return ResponseEntity.ok("Deleted");
        } catch (IOException e) {
            LOGGER.error("Failed to delete chunks for fileId {}: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * SERVICE: Quick check endpoint to confirm the server is running.
     * Web URL: GET http://localhost:8081/internal/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }
}
