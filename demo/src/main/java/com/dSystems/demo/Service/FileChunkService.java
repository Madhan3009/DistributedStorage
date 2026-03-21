package com.dSystems.demo.Service;

import com.dSystems.demo.Config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

@Service
public class FileChunkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileChunkService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path tempPath;
    private final Path uploadPath;
    private final int maxChunksPerFile;
    private final FileChunkMetadataService fileChunkMetadataService;

    public FileChunkService(StorageProperties storageProperties,
                            FileChunkMetadataService fileChunkMetadataService) throws IOException {
        this.tempPath = Path.of(storageProperties.getTempDir()).toAbsolutePath().normalize();
        this.uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        this.maxChunksPerFile = storageProperties.getMaxChunksPerFile();
        this.fileChunkMetadataService = fileChunkMetadataService;
        Files.createDirectories(this.tempPath);
        Files.createDirectories(this.uploadPath);
    }

    public ChunkUploadResult uploadChunk(MultipartFile fileChunk,
                                         int chunkNumber,
                                         int totalChunks,
                                         String identifier) throws IOException {
        String originalFileName = fileChunk.getOriginalFilename();
        if (fileChunk.isEmpty()) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Chunk file is empty");
        }
        if (originalFileName == null || originalFileName.isBlank() || originalFileName.contains("..")) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        if (totalChunks != maxChunksPerFile) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST,
                    "This API currently supports exactly " + maxChunksPerFile + " chunks per file");
        }
        if (totalChunks <= 0 || chunkNumber < 0 || chunkNumber >= totalChunks) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid chunk metadata");
        }
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid identifier");
        }

        Path chunkDirectory = tempPath.resolve(identifier).normalize();
        if (!chunkDirectory.startsWith(tempPath)) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid chunk path");
        }

        Files.createDirectories(chunkDirectory);

        Path chunkFile = chunkDirectory.resolve("chunk-" + chunkNumber + ".part");
        Path metadataFile = fileChunkMetadataService.metadataPath(chunkDirectory);

        try {
            Files.copy(fileChunk.getInputStream(), chunkFile, StandardCopyOption.REPLACE_EXISTING);
            fileChunkMetadataService.updateMetadata(
                    chunkDirectory,
                    identifier,
                    originalFileName,
                    totalChunks,
                    chunkNumber,
                    chunkFile
            );

            long storedChunkCount;
            try (var chunkFiles = Files.list(chunkDirectory)) {
                storedChunkCount = chunkFiles
                        .filter(path -> path.getFileName().toString().matches("chunk-\\d+\\.part"))
                        .count();
            }

            if (storedChunkCount == totalChunks) {
                LOGGER.info("All {} chunks received for {}", totalChunks, identifier);
                return new ChunkUploadResult(HttpStatus.OK, "All chunks received for " + originalFileName);
            }

            LOGGER.info("Stored chunk {} of {} for {}", chunkNumber, totalChunks, identifier);
            return new ChunkUploadResult(HttpStatus.ACCEPTED,
                    "Chunk " + chunkNumber + " of " + totalChunks + " uploaded successfully");
        } catch (IOException io) {
            LOGGER.error("Could not upload chunk {} for file {}: {}", chunkNumber, originalFileName, io.getMessage());
            return new ChunkUploadResult(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not upload chunk " + chunkNumber + ": " + io.getMessage());
        }
    }

    public FileRebuildResult rebuildFile(String identifier) throws IOException {
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return new FileRebuildResult(HttpStatus.BAD_REQUEST, "Invalid identifier");
        }

        Path chunkDirectory = tempPath.resolve(identifier).normalize();
        if (!chunkDirectory.startsWith(tempPath) || !Files.exists(chunkDirectory)) {
            return new FileRebuildResult(HttpStatus.NOT_FOUND, "Chunk directory not found for identifier: " + identifier);
        }

        Path metadataFile = fileChunkMetadataService.metadataPath(chunkDirectory);
        if (!Files.exists(metadataFile)) {
            return new FileRebuildResult(HttpStatus.NOT_FOUND, "Metadata file not found for identifier: " + identifier);
        }

        try {
            Path rebuiltFile = fileChunkMetadataService.rebuildFile(metadataFile, uploadPath);
            LOGGER.info("Rebuilt file {} for {}", rebuiltFile, identifier);
            return new FileRebuildResult(HttpStatus.OK, "File rebuilt successfully at: " + rebuiltFile);
        } catch (IOException io) {
            LOGGER.error("Could not rebuild file for {}: {}", identifier, io.getMessage());
            return new FileRebuildResult(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not rebuild file for " + identifier + ": " + io.getMessage());
        }
    }

    public record ChunkUploadResult(HttpStatus status, String message) {
    }

    public record FileRebuildResult(HttpStatus status, String message) {
    }
}
