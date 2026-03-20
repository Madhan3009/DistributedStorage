package com.dSystems.demo.Controller;

import com.dSystems.demo.Config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.regex.Pattern;

@RestController
@EnableAsync
@RequestMapping("/files")
public class FileChunkController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileChunkController.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path tempPath;
    private final Path uploadPath;
    private final int maxChunksPerFile;

    public FileChunkController(StorageProperties storageProperties) throws IOException {
        this.tempPath = Path.of(storageProperties.getTempDir()).toAbsolutePath().normalize();
        this.uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        this.maxChunksPerFile = storageProperties.getMaxChunksPerFile();
        Files.createDirectories(this.tempPath);
        Files.createDirectories(this.uploadPath);
    }

    @PostMapping("/chunks")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile fileChunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("identifier") String identifier
    ) throws IOException {
        String originalFileName = fileChunk.getOriginalFilename();
        if (fileChunk.isEmpty()) {
            return ResponseEntity.badRequest().body("Chunk file is empty");
        }
        if (originalFileName == null || originalFileName.isBlank() || originalFileName.contains("..")) {
            return ResponseEntity.badRequest().body("Invalid file name");
        }
        if (totalChunks != maxChunksPerFile) {
            return ResponseEntity.badRequest()
                    .body("This API currently supports exactly " + maxChunksPerFile + " chunks per file");
        }
        if (totalChunks <= 0 || chunkNumber < 0 || chunkNumber >= totalChunks) {
            return ResponseEntity.badRequest().body("Invalid chunk metadata");
        }
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return ResponseEntity.badRequest().body("Invalid identifier");
        }

        Path chunkDirectory = tempPath.resolve(identifier).normalize();
        if (!chunkDirectory.startsWith(tempPath)) {
            return ResponseEntity.badRequest().body("Invalid chunk path");
        }

        Files.createDirectories(chunkDirectory);

        Path chunkFile = chunkDirectory.resolve("chunk-" + chunkNumber + ".part");
        Path metadataFile = chunkDirectory.resolve("metadata.properties");

        try {
            Files.copy(fileChunk.getInputStream(), chunkFile, StandardCopyOption.REPLACE_EXISTING);
            updateMetadata(metadataFile, identifier, originalFileName, totalChunks, chunkDirectory, chunkNumber, chunkFile);

            long storedChunkCount = Files.list(chunkDirectory)
                    .filter(path -> path.getFileName().toString().matches("chunk-\\d+\\.part"))
                    .count();

            if (storedChunkCount == totalChunks) {
                LOGGER.info("All {} chunks received for {}", totalChunks, identifier);
                return ResponseEntity.ok("All chunks received for " + originalFileName);
            }

            LOGGER.info("Stored chunk {} of {} for {}", chunkNumber, totalChunks, identifier);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body("Chunk " + chunkNumber + " of " + totalChunks + " uploaded successfully");
        } catch (IOException io) {
            LOGGER.error("Could not upload chunk {} for file {}: {}", chunkNumber, originalFileName, io.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Could not upload chunk " + chunkNumber + ": " + io.getMessage());
        }
    }

    private void updateMetadata(Path metadataFile,
                                String identifier,
                                String originalFileName,
                                int totalChunks,
                                Path chunkDirectory,
                                int chunkNumber,
                                Path chunkFile) throws IOException {
        Properties metadata = new Properties();
        if (Files.exists(metadataFile)) {
            try (InputStream inputStream = Files.newInputStream(metadataFile)) {
                metadata.load(inputStream);
            }
        }

        metadata.setProperty("fileId", identifier);
        metadata.setProperty("originalFileName", originalFileName);
        metadata.setProperty("totalChunks", String.valueOf(totalChunks));
        metadata.setProperty("tempDirectory", chunkDirectory.toString());
        metadata.setProperty("chunk." + chunkNumber, chunkFile.toString());

        try (OutputStream outputStream = Files.newOutputStream(metadataFile)) {
            metadata.store(outputStream, "Chunk upload metadata");
        }
    }
}
