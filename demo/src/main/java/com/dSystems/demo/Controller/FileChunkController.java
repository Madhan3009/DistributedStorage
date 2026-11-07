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
 * User-facing file API on the coordinator: chunked and whole-file upload, server-side rebuild,
 * file listing, and streaming download. All operations are scoped to the authenticated user.
 */
@RestController
@EnableAsync
@RequestMapping("/files")
public class FileChunkController {
    
    private final FileChunkService fileChunkService;

    public FileChunkController(FileChunkService fileChunkService) {
        this.fileChunkService = fileChunkService;
    }

    /**
     * Uploads and replicates a single chunk of a file.
     * {@code POST /files/chunks}
     *
     * @param fileChunk chunk contents
     * @param chunkNumber zero-based chunk index
     * @param totalChunks total number of chunks in the file
     * @param identifier unique file identifier
     * @param authentication the authenticated caller
     */
    @PostMapping("/chunks")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile fileChunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("identifier") String identifier,
            Authentication authentication
    ) throws IOException {
        String username = authentication.getName();
        
        FileChunkService.ChunkUploadResult result =
                fileChunkService.uploadChunk(fileChunk, chunkNumber, totalChunks, identifier, username);
        
        return ResponseEntity.status(result.status()).body(result.message());
    }

    /**
     * Uploads a complete file, which the coordinator splits into chunks and distributes.
     * {@code POST /files/upload}
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        String username = authentication.getName();
        FileChunkService.ChunkUploadResult result = fileChunkService.uploadFile(file, username);
        return ResponseEntity.status(result.status()).body(result.message());
    }

    /**
     * Rebuilds a file from its chunks into the coordinator's upload directory.
     * {@code POST /files/rebuild}
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
     * Lists files owned by the authenticated user.
     * {@code GET /files}
     */
    @GetMapping
    public ResponseEntity<List<FileIndex>> listFiles(Authentication authentication) {
        String username = authentication.getName();
        List<FileIndex> files = fileChunkService.listFiles(username);
        return ResponseEntity.ok(files);
    }

    /**
     * Streams a file to the client one chunk at a time, so the coordinator never holds the whole
     * file in memory.
     * {@code GET /files/download}
     */
    @GetMapping("/download")
    public void downloadFile(
            @RequestParam("identifier") String identifier,
            Authentication authentication,
            jakarta.servlet.http.HttpServletResponse response
    ) throws IOException {
        String username = authentication.getName();
        
        var fileIndexOpt = fileChunkService.getFileIndex(identifier);
        if (fileIndexOpt.isEmpty()) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        var fileIndex = fileIndexOpt.get();
        // Only the owner may download a file.
        if (!fileIndex.getOwnerUsername().equals(username)) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileIndex.getFileName() + "\"");
        
        try {
            fileChunkService.downloadFile(identifier, username, response.getOutputStream());
            response.flushBuffer(); // Flush any buffered response data to the client.
        } catch (IllegalArgumentException e) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
