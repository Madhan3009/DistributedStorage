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
 * Chunk storage API exposed by each storage node for coordinator-to-node traffic: store, fetch,
 * and delete chunks on local disk, plus a health check. Clients should use the coordinator's
 * {@code /files} API instead.
 */
@RestController
@RequestMapping("/internal")
public class InternalNodeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalNodeController.class);

    // Root directory for chunk files on this node.
    private final Path storagePath;

    public InternalNodeController(StorageProperties storageProperties) throws IOException {
        this.storagePath = Path.of(storageProperties.getTempDir()).toAbsolutePath().normalize();
        Files.createDirectories(this.storagePath);
    }

    /**
     * Writes a chunk to local disk, replacing any existing copy.
     * {@code POST /internal/store}
     *
     * @param fileId unique file identifier
     * @param chunkNumber zero-based chunk index
     * @param chunkFile chunk contents
     */
    @PostMapping("/store")
    public ResponseEntity<String> storeChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("file") MultipartFile chunkFile
    ) {
        try {
            Path fileDir = storagePath.resolve(fileId).normalize();
            
            // Reject identifiers that resolve outside the storage root (path traversal).
            if (!fileDir.startsWith(storagePath)) {
                return ResponseEntity.badRequest().body("Invalid file path");
            }
            Files.createDirectories(fileDir);

            Path targetFile = fileDir.resolve("chunk-" + chunkNumber + ".part");
            
            Files.copy(chunkFile.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Successfully stored chunk {} for fileId {}", chunkNumber, fileId);
            return ResponseEntity.ok("Stored");
        } catch (IOException e) {
            LOGGER.error("Failed to store chunk {} for fileId {}: {}", chunkNumber, fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Returns a stored chunk as a binary attachment.
     * {@code GET /internal/fetch}
     */
    @GetMapping("/fetch")
    public ResponseEntity<Resource> fetchChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("chunkNumber") int chunkNumber
    ) {
        Path targetFile = storagePath.resolve(fileId).resolve("chunk-" + chunkNumber + ".part").normalize();
        
        // Treat paths outside the storage root the same as missing chunks.
        if (!targetFile.startsWith(storagePath) || !Files.exists(targetFile)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = new FileSystemResource(targetFile.toFile());
        
        return ResponseEntity.ok()
                 .contentType(MediaType.APPLICATION_OCTET_STREAM)
                 .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chunk-" + chunkNumber + ".part\"")
                 .body(resource);
    }

    /**
     * Deletes all chunks of a file. Succeeds if the file's directory does not exist.
     * {@code DELETE /internal/delete}
     */
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteChunks(@RequestParam("fileId") String fileId) {
        try {
            Path fileDir = storagePath.resolve(fileId).normalize();
            
            // Nothing to delete for out-of-root paths or directories that no longer exist.
            if (!fileDir.startsWith(storagePath) || !Files.exists(fileDir)) {
                return ResponseEntity.ok("Deleted (directory did not exist)");
            }
            
            try (var paths = Files.walk(fileDir)) {
                paths.sorted(Comparator.reverseOrder()) // Delete children before their parent directory.
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
     * Liveness check.
     * {@code GET /internal/health}
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UP");
    }
}
