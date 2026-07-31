package com.dSystems.demo.Controller;

import com.dSystems.demo.Model.FileIndex;
import com.dSystems.demo.Service.FileChunkService;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * THE COORDINATOR'S FILE OPERATIONS DESK.
 * 
 * Think of this class as the receptionist window in charge of handling user files.
 * Users connect to this desk to:
 * 1. Upload a single piece (chunk) of a file.
 * 2. Request the system to reassemble their pieces into a whole file on the server.
 * 3. List all files they have uploaded.
 * 4. Download a file (where the coordinator fetches all pieces on-the-fly and streams them back).
 * 
 * Annotations:
 * - @RestController: Marks this as a JSON-producing web endpoint controller.
 * - @RequestMapping("/files"): Prepends "/files" to all web paths in this file.
 */
@RestController
@EnableAsync
@RequestMapping("/files")
public class FileChunkController {
    
    // Injected service that handles the actual file splitting/merging logic.
    private final FileChunkService fileChunkService;

    /**
     * Constructor - Receives the file service.
     */
    public FileChunkController(FileChunkService fileChunkService) {
        this.fileChunkService = fileChunkService;
    }

    /**
     * SERVICE: Upload a single chunk.
     * Web URL: POST http://localhost:8080/files/chunks
     * 
     * @param fileChunk The actual raw binary bytes of the chunk.
     * @param chunkNumber The index of this chunk.
     * @param totalChunks How many total chunks make up the file.
     * @param identifier The unique barcode/ID of the file.
     * @param authentication Injected by Spring Security, representing the currently logged-in user.
     */
    @PostMapping("/chunks")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile fileChunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("identifier") String identifier,
            Authentication authentication
    ) throws IOException {
        // Grab the name of the user from the login token
        String username = authentication.getName();
        
        // Pass the chunk to our helper service to save and replicate
        FileChunkService.ChunkUploadResult result =
                fileChunkService.uploadChunk(fileChunk, chunkNumber, totalChunks, identifier, username);
        
        return ResponseEntity.status(result.status()).body(result.message());
    }

    /**
     * SERVICE: Stitch the file back together on the server.
     * Web URL: POST http://localhost:8080/files/rebuild
     */
    @PostMapping("/rebuild")
    public ResponseEntity<String> rebuildFile(
            @RequestParam("identifier") String identifier,
            Authentication authentication
    ) throws IOException {
        String username = authentication.getName();
        FileChunkService.FileRebuildResult result = fileChunkService.rebuildFile(identifier, username);
        return ResponseEntity.status(result.status()).body(result.message());
    }

    /**
     * SERVICE: List all files uploaded by the logged-in user.
     * Web URL: GET http://localhost:8080/files
     */
    @GetMapping
    public ResponseEntity<List<FileIndex>> listFiles(Authentication authentication) {
        String username = authentication.getName();
        List<FileIndex> files = fileChunkService.listFiles(username);
        return ResponseEntity.ok(files);
    }

    /**
     * SERVICE: Download a file.
     * Web URL: GET http://localhost:8080/files/download
     * 
     * This streams the file back piece-by-piece so the coordinator server doesn't
     * run out of memory trying to load large files all at once.
     */
    @GetMapping("/download")
    public void downloadFile(
            @RequestParam("identifier") String identifier,
            Authentication authentication,
            jakarta.servlet.http.HttpServletResponse response
    ) throws IOException {
        String username = authentication.getName();
        
        // Step 1: Find the file index in the database catalog
        var fileIndexOpt = fileChunkService.getFileIndex(identifier);
        if (fileIndexOpt.isEmpty()) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        var fileIndex = fileIndexOpt.get();
        // Step 2: Ensure the logged-in user actually owns this file
        if (!fileIndex.getOwnerUsername().equals(username)) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Step 3: Set headers telling the browser that this is a raw binary file stream
        // and that it should be downloaded as a file attachment with its original name.
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileIndex.getFileName() + "\"");
        
        try {
            // Step 4: Stream the file parts directly into the browser's download channel
            fileChunkService.downloadFile(identifier, username, response.getOutputStream());
            response.flushBuffer(); // Force the network buffers to empty/send
        } catch (IllegalArgumentException e) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
