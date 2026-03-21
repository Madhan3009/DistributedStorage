package com.dSystems.demo.Controller;

import com.dSystems.demo.Service.FileChunkService;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
            @RequestParam("identifier") String identifier
    ) throws IOException {
        FileChunkService.ChunkUploadResult result =
                fileChunkService.uploadChunk(fileChunk, chunkNumber, totalChunks, identifier);
        return ResponseEntity.status(result.status()).body(result.message());
    }

    @PostMapping("/rebuild")
    public ResponseEntity<String> rebuildFile(@RequestParam("identifier") String identifier) throws IOException {
        FileChunkService.FileRebuildResult result = fileChunkService.rebuildFile(identifier);
        return ResponseEntity.status(result.status()).body(result.message());
    }
}
