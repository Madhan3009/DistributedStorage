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

@RestController
@EnableAsync
@RequestMapping("/files")
public class FileChunkController {
    private final FileChunkService fileChunkService;

    public FileChunkController(FileChunkService fileChunkService) {
        this.fileChunkService = fileChunkService;
    }

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

    @PostMapping("/rebuild")
    public ResponseEntity<String> rebuildFile(
            @RequestParam("identifier") String identifier,
            Authentication authentication
    ) throws IOException {
        String username = authentication.getName();
        FileChunkService.FileRebuildResult result = fileChunkService.rebuildFile(identifier, username);
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @GetMapping
    public ResponseEntity<List<FileIndex>> listFiles(Authentication authentication) {
        String username = authentication.getName();
        List<FileIndex> files = fileChunkService.listFiles(username);
        return ResponseEntity.ok(files);
    }

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
        if (!fileIndex.getOwnerUsername().equals(username)) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileIndex.getFileName() + "\"");
        
        try {
            fileChunkService.downloadFile(identifier, username, response.getOutputStream());
            response.flushBuffer();
        } catch (IllegalArgumentException e) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
