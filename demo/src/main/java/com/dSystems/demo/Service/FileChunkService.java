package com.dSystems.demo.Service;

import com.dSystems.demo.Config.StorageProperties;
import com.dSystems.demo.Model.ChunkPlacement;
import com.dSystems.demo.Model.FileIndex;
import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Repository.ChunkPlacementRepository;
import com.dSystems.demo.Repository.FileIndexRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class FileChunkService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileChunkService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path tempPath;
    private final Path uploadPath;
    private final int maxChunksPerFile;
    private final StorageProperties storageProperties;
    private final FileIndexRepository fileIndexRepository;
    private final NodeRegistryService nodeRegistryService;
    private final ConsistentHashRing consistentHashRing;
    private final ChunkPlacementRepository chunkPlacementRepository;

    public FileChunkService(StorageProperties storageProperties,
                            FileIndexRepository fileIndexRepository,
                            NodeRegistryService nodeRegistryService,
                            ConsistentHashRing consistentHashRing,
                            ChunkPlacementRepository chunkPlacementRepository) throws IOException {
        this.tempPath = Path.of(storageProperties.getTempDir(), "coordinator-staging").toAbsolutePath().normalize();
        this.uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        this.maxChunksPerFile = storageProperties.getMaxChunksPerFile();
        this.storageProperties = storageProperties;
        this.fileIndexRepository = fileIndexRepository;
        this.nodeRegistryService = nodeRegistryService;
        this.consistentHashRing = consistentHashRing;
        this.chunkPlacementRepository = chunkPlacementRepository;
        Files.createDirectories(this.tempPath);
        Files.createDirectories(this.uploadPath);
    }

    public ChunkUploadResult uploadChunk(MultipartFile fileChunk,
                                         int chunkNumber,
                                         int totalChunks,
                                         String identifier,
                                         String username) throws IOException {
        String originalFileName = fileChunk.getOriginalFilename();
        if (fileChunk.isEmpty()) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Chunk file is empty");
        }
        if (originalFileName == null || originalFileName.isBlank() || originalFileName.contains("..")) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid file name");
        }
        if (totalChunks > maxChunksPerFile) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST,
                    "This API currently supports at most " + maxChunksPerFile + " chunks per file");
        }
        if (totalChunks <= 0 || chunkNumber < 0 || chunkNumber >= totalChunks) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid chunk metadata");
        }
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid identifier");
        }

        // Get alive nodes
        List<StorageNode> aliveNodes = nodeRegistryService.getAliveNodes();
        if (aliveNodes.isEmpty()) {
            return new ChunkUploadResult(HttpStatus.SERVICE_UNAVAILABLE, "No alive storage nodes available");
        }

        Path chunkDirectory = tempPath.resolve(identifier).normalize();
        if (!chunkDirectory.startsWith(tempPath)) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid chunk path");
        }

        // Manage FileIndex in DB
        FileIndex fileIndex = fileIndexRepository.findByFileId(identifier).orElse(null);
        if (fileIndex == null) {
            fileIndex = new FileIndex();
            fileIndex.setFileId(identifier);
            fileIndex.setFileName(originalFileName);
            fileIndex.setTotalChunks(totalChunks);
            fileIndex.setOwnerUsername(username);
            fileIndex.setUploadedAt(LocalDateTime.now());
            fileIndex.setStatus("PENDING");
            fileIndex.setFileSize(0L);
            fileIndex = fileIndexRepository.save(fileIndex);
        } else {
            if (!fileIndex.getOwnerUsername().equals(username)) {
                return new ChunkUploadResult(HttpStatus.FORBIDDEN, "Access denied: file owned by another user");
            }
        }

        Files.createDirectories(chunkDirectory);
        Path chunkFile = chunkDirectory.resolve("chunk-" + chunkNumber + ".part");

        try {
            Files.copy(fileChunk.getInputStream(), chunkFile, StandardCopyOption.REPLACE_EXISTING);

            // Select target nodes using Consistent Hash Ring
            int replicationFactor = storageProperties.getReplicationFactor();
            List<StorageNode> targetNodes = consistentHashRing.getNodesForKey(
                    identifier + "-chunk-" + chunkNumber,
                    replicationFactor,
                    aliveNodes
            );

            if (targetNodes.isEmpty()) {
                return new ChunkUploadResult(HttpStatus.SERVICE_UNAVAILABLE, "Could not assign storage nodes");
            }

            // Replicate chunk to target nodes
            RestTemplate restTemplate = new RestTemplate();
            int replicaIndex = 0;

            // Remove existing placements for this chunk if any
            List<ChunkPlacement> existingPlacements = chunkPlacementRepository.findByFileIdAndChunkNumber(identifier, chunkNumber);
            chunkPlacementRepository.deleteAll(existingPlacements);

            for (StorageNode targetNode : targetNodes) {
                String url = String.format("http://%s:%d/internal/store", targetNode.getHost(), targetNode.getPort());
                try {
                    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("fileId", identifier);
                    body.add("chunkNumber", chunkNumber);
                    body.add("file", new FileSystemResource(chunkFile.toFile()));

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                    HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                    restTemplate.postForEntity(url, requestEntity, String.class);

                    // Save placement info in DB
                    ChunkPlacement placement = new ChunkPlacement();
                    placement.setFileId(identifier);
                    placement.setChunkNumber(chunkNumber);
                    placement.setNodeId(targetNode.getNodeId());
                    placement.setReplicaIndex(replicaIndex++);
                    chunkPlacementRepository.save(placement);
                } catch (Exception e) {
                    LOGGER.error("Failed to replicate chunk {} to node {}: {}", chunkNumber, targetNode.getNodeId(), e.getMessage());
                }
            }

            if (replicaIndex == 0) {
                return new ChunkUploadResult(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store chunk on any target node");
            }

            // Check if all chunks are fully uploaded (we need at least one replica for each chunk)
            List<ChunkPlacement> allPlacements = chunkPlacementRepository.findByFileId(identifier);
            Set<Integer> uploadedChunks = new HashSet<>();
            for (ChunkPlacement cp : allPlacements) {
                uploadedChunks.add(cp.getChunkNumber());
            }

            if (uploadedChunks.size() == totalChunks) {
                // Calculate total file size
                long totalSize = 0;
                for (int i = 0; i < totalChunks; i++) {
                    Path partFile = chunkDirectory.resolve("chunk-" + i + ".part");
                    if (Files.exists(partFile)) {
                        totalSize += Files.size(partFile);
                    }
                }
                fileIndex.setStatus("COMPLETE");
                fileIndex.setFileSize(totalSize);
                fileIndexRepository.save(fileIndex);

                // Optional: Clean up local coordinator temp staging folder
                try (var paths = Files.walk(chunkDirectory)) {
                    paths.sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(java.io.File::delete);
                } catch (IOException e) {
                    LOGGER.warn("Could not clean up coordinator staging dir: {}", e.getMessage());
                }

                LOGGER.info("All {} chunks received and replicated for {}", totalChunks, identifier);
                return new ChunkUploadResult(HttpStatus.OK, "All chunks received and replicated for " + originalFileName);
            }

            LOGGER.info("Replicated chunk {} of {} for {}", chunkNumber, totalChunks, identifier);
            return new ChunkUploadResult(HttpStatus.ACCEPTED,
                    "Chunk " + chunkNumber + " of " + totalChunks + " uploaded and replicated successfully");
        } catch (IOException io) {
            LOGGER.error("Could not upload chunk {} for file {}: {}", chunkNumber, originalFileName, io.getMessage());
            return new ChunkUploadResult(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not upload chunk " + chunkNumber + ": " + io.getMessage());
        }
    }

    public FileRebuildResult rebuildFile(String identifier, String username) throws IOException {
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            return new FileRebuildResult(HttpStatus.BAD_REQUEST, "Invalid identifier");
        }

        var fileIndexOpt = fileIndexRepository.findByFileId(identifier);
        if (fileIndexOpt.isEmpty()) {
            return new FileRebuildResult(HttpStatus.NOT_FOUND, "File index not found for identifier: " + identifier);
        }
        var fileIndex = fileIndexOpt.get();
        if (!fileIndex.getOwnerUsername().equals(username)) {
            return new FileRebuildResult(HttpStatus.FORBIDDEN, "Access denied: file owned by another user");
        }

        int totalChunks = fileIndex.getTotalChunks();
        Files.createDirectories(uploadPath);
        Path rebuiltFile = uploadPath.resolve(fileIndex.getFileName()).normalize();

        List<StorageNode> aliveNodes = nodeRegistryService.getAliveNodes();
        RestTemplate restTemplate = new RestTemplate();

        try (OutputStream outputStream = Files.newOutputStream(rebuiltFile)) {
            for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
                List<ChunkPlacement> placements = chunkPlacementRepository.findByFileIdAndChunkNumber(identifier, chunkNumber);
                if (placements.isEmpty()) {
                    throw new IOException("No chunk placement record found for chunk " + chunkNumber);
                }

                byte[] chunkBytes = null;
                for (ChunkPlacement placement : placements) {
                    Optional<StorageNode> targetNodeOpt = aliveNodes.stream()
                            .filter(n -> n.getNodeId().equals(placement.getNodeId()))
                            .findFirst();
                    if (targetNodeOpt.isPresent()) {
                        StorageNode targetNode = targetNodeOpt.get();
                        String url = String.format("http://%s:%d/internal/fetch?fileId=%s&chunkNumber=%d",
                                targetNode.getHost(), targetNode.getPort(), identifier, chunkNumber);
                        try {
                            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                                chunkBytes = response.getBody();
                                break;
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to fetch chunk {} from node {}: {}", 
                                    chunkNumber, targetNode.getNodeId(), e.getMessage());
                        }
                    }
                }

                if (chunkBytes == null) {
                    throw new IOException("Failed to fetch chunk " + chunkNumber + " from any alive replica node");
                }

                outputStream.write(chunkBytes);
            }
        }

        LOGGER.info("Rebuilt file {} for {}", rebuiltFile, identifier);
        return new FileRebuildResult(HttpStatus.OK, "File rebuilt successfully at: " + rebuiltFile);
    }

    public List<FileIndex> listFiles(String username) {
        return fileIndexRepository.findByOwnerUsername(username);
    }

    public Optional<FileIndex> getFileIndex(String identifier) {
        return fileIndexRepository.findByFileId(identifier);
    }

    public void downloadFile(String identifier, String username, OutputStream responseOutputStream) throws IOException {
        var fileIndexOpt = fileIndexRepository.findByFileId(identifier);
        if (fileIndexOpt.isEmpty()) {
            throw new IllegalArgumentException("File index not found");
        }
        var fileIndex = fileIndexOpt.get();
        if (!fileIndex.getOwnerUsername().equals(username)) {
            throw new IllegalArgumentException("Access denied");
        }

        int totalChunks = fileIndex.getTotalChunks();
        List<StorageNode> aliveNodes = nodeRegistryService.getAliveNodes();
        RestTemplate restTemplate = new RestTemplate();

        for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
            List<ChunkPlacement> placements = chunkPlacementRepository.findByFileIdAndChunkNumber(identifier, chunkNumber);
            if (placements.isEmpty()) {
                throw new IOException("No chunk placement record found for chunk " + chunkNumber);
            }

            byte[] chunkBytes = null;
            for (ChunkPlacement placement : placements) {
                Optional<StorageNode> targetNodeOpt = aliveNodes.stream()
                        .filter(n -> n.getNodeId().equals(placement.getNodeId()))
                        .findFirst();
                if (targetNodeOpt.isPresent()) {
                    StorageNode targetNode = targetNodeOpt.get();
                    String url = String.format("http://%s:%d/internal/fetch?fileId=%s&chunkNumber=%d",
                            targetNode.getHost(), targetNode.getPort(), identifier, chunkNumber);
                    try {
                        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                            chunkBytes = response.getBody();
                            break;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to fetch chunk {} from node {}: {}", 
                                chunkNumber, targetNode.getNodeId(), e.getMessage());
                    }
                }
            }

            if (chunkBytes == null) {
                throw new IOException("Failed to fetch chunk " + chunkNumber + " from any alive replica node");
            }

            responseOutputStream.write(chunkBytes);
        }
    }

    public record ChunkUploadResult(HttpStatus status, String message) {
    }

    public record FileRebuildResult(HttpStatus status, String message) {
    }
}
