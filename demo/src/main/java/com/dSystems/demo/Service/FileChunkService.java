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

/**
 * THE DISTRIBUTED FILE COORDINATOR ENGINE (THE MASTER WORKER).
 * 
 * Think of this service as the "operations manager" of the storage system. 
 * When a user uploads a file, this class is responsible for:
 * 1. Validating the file and splitting it into numbered pieces (chunks).
 * 2. Finding out which storage nodes (worker computers) are currently online.
 * 3. Deciding where to save copies of each piece using a virtual hash ring (so files are distributed evenly).
 * 4. Sending the pieces to those storage nodes over the network.
 * 5. Documenting the location of each piece in the database catalog.
 * 
 * When a user downloads a file, this class reverses the process:
 * 1. It looks up the locations of all pieces in the database.
 * 2. It fetches the pieces from active storage nodes.
 * 3. It stitches the pieces back together in the correct order to recreate the original file.
 * 
 * Annotations:
 * - @Service: Tells Spring Boot to create a single instance of this service and make it available to the rest of the application.
 */
@Service
public class FileChunkService {
    // Logger to print operational notes to the system console.
    private static final Logger LOGGER = LoggerFactory.getLogger(FileChunkService.class);
    
    // A regular expression pattern to ensure file IDs contain only safe characters (letters, numbers, dots, dashes).
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]+");

    // File path directories
    private final Path tempPath;
    private final Path uploadPath;
    
    // Hard limits loaded from configuration
    private final int maxChunksPerFile;
    private final StorageProperties storageProperties;
    
    // Database clerks (Repositories) and Helper services:
    private final FileIndexRepository fileIndexRepository;
    private final NodeRegistryService nodeRegistryService;
    private final ConsistentHashRing consistentHashRing;
    private final ChunkPlacementRepository chunkPlacementRepository;

    /**
     * Constructor - Sets up folders and dependencies.
     * This is run once when the application boots up.
     */
    public FileChunkService(StorageProperties storageProperties,
                            FileIndexRepository fileIndexRepository,
                            NodeRegistryService nodeRegistryService,
                            ConsistentHashRing consistentHashRing,
                            ChunkPlacementRepository chunkPlacementRepository) throws IOException {
        // Resolve absolute folder paths on the computer's hard drive
        this.tempPath = Path.of(storageProperties.getTempDir(), "coordinator-staging").toAbsolutePath().normalize();
        this.uploadPath = Path.of(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        this.maxChunksPerFile = storageProperties.getMaxChunksPerFile();
        this.storageProperties = storageProperties;
        this.fileIndexRepository = fileIndexRepository;
        this.nodeRegistryService = nodeRegistryService;
        this.consistentHashRing = consistentHashRing;
        this.chunkPlacementRepository = chunkPlacementRepository;
        
        // Create the folders on disk if they don't exist yet
        Files.createDirectories(this.tempPath);
        Files.createDirectories(this.uploadPath);
    }

    /**
     * Receives and processes a single chunk (segment) of a file.
     * 
     * @param fileChunk The actual raw bytes of the file part.
     * @param chunkNumber The index number of this part (e.g. piece 0, 1, 2).
     * @param totalChunks The total number of pieces that make up the complete file.
     * @param identifier The unique barcode/ID of the file.
     * @param username The user who is uploading the file.
     */
    public ChunkUploadResult uploadChunk(MultipartFile fileChunk,
                                         int chunkNumber,
                                         int totalChunks,
                                         String identifier,
                                         String username) throws IOException {
        String originalFileName = fileChunk.getOriginalFilename();
        
        // --- STEP 1: SAFETY CHECKLIST ---
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

        // Get list of active storage servers
        List<StorageNode> aliveNodes = nodeRegistryService.getAliveNodes();
        if (aliveNodes.isEmpty()) {
            return new ChunkUploadResult(HttpStatus.SERVICE_UNAVAILABLE, "No alive storage nodes available");
        }

        // Check if the temporary folder path is safe from directory traversal hacks
        Path chunkDirectory = tempPath.resolve(identifier).normalize();
        if (!chunkDirectory.startsWith(tempPath)) {
            return new ChunkUploadResult(HttpStatus.BAD_REQUEST, "Invalid chunk path");
        }

        // --- STEP 2: CREATE OR LOAD THE CATALOG RECORD ---
        FileIndex fileIndex = fileIndexRepository.findByFileId(identifier).orElse(null);
        if (fileIndex == null) {
            // First time we see this file, create a new catalog record.
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
            // File already exists, ensure it is owned by the person uploading.
            if (!fileIndex.getOwnerUsername().equals(username)) {
                return new ChunkUploadResult(HttpStatus.FORBIDDEN, "Access denied: file owned by another user");
            }
        }

        // Create a subfolder for this file's chunks on the coordinator
        Files.createDirectories(chunkDirectory);
        Path chunkFile = chunkDirectory.resolve("chunk-" + chunkNumber + ".part");

        try {
            // Save the received chunk bytes temporarily on the coordinator
            Files.copy(fileChunk.getInputStream(), chunkFile, StandardCopyOption.REPLACE_EXISTING);

            // --- STEP 3: ASSIGN DESTINATION SERVERS (REPLICAS) ---
            // We want to save multiple copies (replication factor) of this chunk to prevent data loss.
            int replicationFactor = storageProperties.getReplicationFactor();
            
            // Consistent Hash Ring assigns the chunk to specific servers based on the chunk name.
            List<StorageNode> targetNodes = consistentHashRing.getNodesForKey(
                    identifier + "-chunk-" + chunkNumber,
                    replicationFactor,
                    aliveNodes
            );

            if (targetNodes.isEmpty()) {
                return new ChunkUploadResult(HttpStatus.SERVICE_UNAVAILABLE, "Could not assign storage nodes");
            }

            // --- STEP 4: DISTRIBUTE COPIES OVER THE NETWORK ---
            RestTemplate restTemplate = new RestTemplate(); // Helper to make HTTP network calls
            int replicaIndex = 0;

            // Remove any old location placement details for this specific chunk
            List<ChunkPlacement> existingPlacements = chunkPlacementRepository.findByFileIdAndChunkNumber(identifier, chunkNumber);
            chunkPlacementRepository.deleteAll(existingPlacements);

            // Send the chunk file to each chosen target server over the network
            for (StorageNode targetNode : targetNodes) {
                String url = String.format("http://%s:%d/internal/store", targetNode.getHost(), targetNode.getPort());
                try {
                    // Pack the file into an HTTP form upload payload
                    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("fileId", identifier);
                    body.add("chunkNumber", chunkNumber);
                    body.add("file", new FileSystemResource(chunkFile.toFile()));

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                    HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                    
                    // Send it!
                    restTemplate.postForEntity(url, requestEntity, String.class);

                    // Document this copy's location in our SQL database placement table
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

            // --- STEP 5: CHECK IF THE WHOLE FILE IS FULLY RECEIVED ---
            List<ChunkPlacement> allPlacements = chunkPlacementRepository.findByFileId(identifier);
            Set<Integer> uploadedChunks = new HashSet<>();
            for (ChunkPlacement cp : allPlacements) {
                uploadedChunks.add(cp.getChunkNumber());
            }

            // If we have at least one copy of every chunk, the file is fully uploaded!
            if (uploadedChunks.size() == totalChunks) {
                // Calculate the final assembled file size by summing all chunk file sizes.
                long totalSize = 0;
                for (int i = 0; i < totalChunks; i++) {
                    Path partFile = chunkDirectory.resolve("chunk-" + i + ".part");
                    if (Files.exists(partFile)) {
                        totalSize += Files.size(partFile);
                    }
                }
                
                // Update catalog status to COMPLETE
                fileIndex.setStatus("COMPLETE");
                fileIndex.setFileSize(totalSize);
                fileIndexRepository.save(fileIndex);

                // Clean up the temporary local chunk files on the Coordinator
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

    /**
     * Rebuilds a file by gathering all its pieces from the servers and saving the assembled file in the uploads folder.
     */
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

        // Open a new file write stream
        try (OutputStream outputStream = Files.newOutputStream(rebuiltFile)) {
            // Loop through each piece in order (0, 1, 2...)
            for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
                // Find all servers that hold copies of this piece
                List<ChunkPlacement> placements = chunkPlacementRepository.findByFileIdAndChunkNumber(identifier, chunkNumber);
                if (placements.isEmpty()) {
                    throw new IOException("No chunk placement record found for chunk " + chunkNumber);
                }

                byte[] chunkBytes = null;
                // Try fetching the piece from the first server that is currently ALIVE
                for (ChunkPlacement placement : placements) {
                    Optional<StorageNode> targetNodeOpt = aliveNodes.stream()
                            .filter(n -> n.getNodeId().equals(placement.getNodeId()))
                            .findFirst();
                    if (targetNodeOpt.isPresent()) {
                        StorageNode targetNode = targetNodeOpt.get();
                        String url = String.format("http://%s:%d/internal/fetch?fileId=%s&chunkNumber=%d",
                                targetNode.getHost(), targetNode.getPort(), identifier, chunkNumber);
                        try {
                            // Download the raw chunk bytes from the node
                            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                                chunkBytes = response.getBody();
                                break; // Successfully fetched, stop trying other servers for this piece
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

                // Write this piece's bytes into the assembled file
                outputStream.write(chunkBytes);
            }
        }

        LOGGER.info("Rebuilt file {} for {}", rebuiltFile, identifier);
        return new FileRebuildResult(HttpStatus.OK, "File rebuilt successfully at: " + rebuiltFile);
    }

    /**
     * Lists all file indexes uploaded by a specific user.
     */
    public List<FileIndex> listFiles(String username) {
        return fileIndexRepository.findByOwnerUsername(username);
    }

    /**
     * Fetches details of a specific file index by its identifier.
     */
    public Optional<FileIndex> getFileIndex(String identifier) {
        return fileIndexRepository.findByFileId(identifier);
    }

    /**
     * Streams the rebuilt file directly back to the user's download request, piece by piece,
     * without wasting local disk space to write the full assembled file first.
     */
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

        // Loop through each piece in order and push it directly to the user's download stream
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

            // Stream the bytes directly to the response output stream (e.g. user's browser download stream)
            responseOutputStream.write(chunkBytes);
        }
    }

    // --- SUB-RECORDS (RESPONSES) ---
    
    public record ChunkUploadResult(HttpStatus status, String message) {
    }

    public record FileRebuildResult(HttpStatus status, String message) {
    }
}
