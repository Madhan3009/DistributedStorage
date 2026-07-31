package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Config.StorageProperties;
import com.dSystems.demo.Model.ChunkPlacement;
import com.dSystems.demo.Model.FileIndex;
import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Repository.ChunkPlacementRepository;
import com.dSystems.demo.Repository.FileIndexRepository;
import com.dSystems.demo.Service.NodeRegistryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * THE CLUSTER SELF-HEALING SYSTEM (THE DOCTOR).
 * 
 * Think of this class as a background "medical doctor" that periodically checks if our file copies
 * are healthy. 
 * If a server crashes (marked "DEAD"), any file chunks stored on it are lost. This class wakes up
 * every 20 seconds, identifies those lost chunks, finds a surviving copy on another healthy server,
 * copies it to a new healthy server, and updates the database records.
 * 
 * This ensures that even if servers crash, we always maintain the required number of backups
 * (replication factor) for every file.
 */
@Component
@ConditionalOnProperty(name = "app.role", havingValue = "COORDINATOR", matchIfMissing = true)
public class ReReplicationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReReplicationScheduler.class);

    private final ChunkPlacementRepository chunkPlacementRepository;
    private final NodeRegistryService nodeRegistryService;
    private final StorageProperties storageProperties;
    private final FileIndexRepository fileIndexRepository;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Constructor - Configures dependencies.
     */
    public ReReplicationScheduler(ChunkPlacementRepository chunkPlacementRepository,
                                  NodeRegistryService nodeRegistryService,
                                  StorageProperties storageProperties,
                                  FileIndexRepository fileIndexRepository,
                                  RestTemplate restTemplate,
                                  MeterRegistry meterRegistry) {
        this.chunkPlacementRepository = chunkPlacementRepository;
        this.nodeRegistryService = nodeRegistryService;
        this.storageProperties = storageProperties;
        this.fileIndexRepository = fileIndexRepository;
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Periodic healing task.
     * Wakes up every 20 seconds (or configurable interval) to repair missing backups.
     */
    @Scheduled(fixedRateString = "${app.healing-interval-ms:20000}")
    @Transactional
    public void healReplicas() {
        // Prevent concurrent execution of the scheduler if a previous run is still in progress
        if (!isRunning.compareAndSet(false, true)) {
            LOGGER.warn("Healing task execution skipped: previous healing task is still running.");
            return;
        }

        try {
            // Step 1: Get all servers and split them into ALIVE and DEAD lists
            List<StorageNode> allNodes = nodeRegistryService.getAllNodes();
            List<StorageNode> aliveNodes = allNodes.stream().filter(n -> "ALIVE".equals(n.getStatus())).toList();
            List<StorageNode> deadNodes = allNodes.stream().filter(n -> "DEAD".equals(n.getStatus())).toList();

            // If no servers are dead, or there are no live servers left to copy data to, we can't heal anything.
            if (deadNodes.isEmpty() || aliveNodes.isEmpty()) {
                return;
            }

            List<String> deadNodeIds = deadNodes.stream().map(StorageNode::getNodeId).toList();
            
            // Find all unique file IDs that have placements on the dead nodes
            List<String> fileIdsToHeal = chunkPlacementRepository.findFileIdsWithPlacementsOnNodes(deadNodeIds);

            for (String fileId : fileIdsToHeal) {
                Optional<FileIndex> fileIndexOpt = fileIndexRepository.findByFileId(fileId);
                if (fileIndexOpt.isEmpty()) {
                    continue;
                }
                FileIndex fileIndex = fileIndexOpt.get();
                int totalChunks = fileIndex.getTotalChunks();
                int targetReplication = storageProperties.getReplicationFactor();

                List<ChunkPlacement> allPlacements = chunkPlacementRepository.findByFileId(fileId);

                for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
                    final int currentChunkNum = chunkNumber;
                    List<ChunkPlacement> chunkPlacements = allPlacements.stream()
                            .filter(cp -> cp.getChunkNumber() == currentChunkNum)
                            .toList();

                    // Filter out the copies that are on healthy servers
                    List<ChunkPlacement> alivePlacements = chunkPlacements.stream()
                            .filter(cp -> aliveNodes.stream().anyMatch(an -> an.getNodeId().equals(cp.getNodeId())))
                            .toList();

                    List<ChunkPlacement> deadPlacementsForChunk = chunkPlacements.stream()
                            .filter(cp -> deadNodes.stream().anyMatch(dn -> dn.getNodeId().equals(cp.getNodeId())))
                            .toList();

                    if (deadPlacementsForChunk.isEmpty()) {
                        // This chunk does not have any placement on the dead nodes, skip it
                        continue;
                    }

                    // Step 3: Check if we have total data loss for this chunk
                    if (alivePlacements.isEmpty()) {
                        LOGGER.error("CRITICAL: All replicas of chunk {} for file {} ({}) are lost! Marking file as CORRUPT.",
                                chunkNumber, fileIndex.getFileName(), fileId);
                        
                        fileIndex.setStatus("CORRUPT");
                        fileIndexRepository.save(fileIndex);
                        
                        // Clean up dead placements in DB since they are no longer accessible
                        chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                        meterRegistry.counter("replication.failed.chunks").increment();
                        continue;
                    }

                    // Step 4: Check if the number of healthy copies has dropped below our safety target
                    if (alivePlacements.size() < targetReplication) {
                        LOGGER.info("Chunk {} of file {} is under-replicated ({} alive, target {}). Healing...",
                                chunkNumber, fileId, alivePlacements.size(), targetReplication);

                        // Find healthy servers that don't already store a copy of this chunk
                        List<StorageNode> candidates = aliveNodes.stream()
                                .filter(an -> chunkPlacements.stream().noneMatch(cp -> cp.getNodeId().equals(an.getNodeId())))
                                .toList();

                        if (candidates.isEmpty()) {
                            LOGGER.warn("No suitable alive nodes found to host replica for chunk {} of file {}",
                                    chunkNumber, fileId);
                            // Clean up dead placement records anyway as we can't heal to new nodes right now
                            chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                            continue;
                        }

                        int neededReplicas = targetReplication - alivePlacements.size();
                        int healSuccessCount = 0;

                        for (int i = 0; i < Math.min(neededReplicas, candidates.size()); i++) {
                            StorageNode newTarget = candidates.get(i);
                            // Pick the first active copy as the source
                            ChunkPlacement sourcePlacement = alivePlacements.get(0);
                            
                            StorageNode sourceNode = aliveNodes.stream()
                                    .filter(an -> an.getNodeId().equals(sourcePlacement.getNodeId()))
                                    .findFirst().orElse(null);

                            if (sourceNode == null) {
                                continue;
                            }

                            // Step 5: Fetch the chunk bytes from the healthy source server
                            String fetchUrl = String.format("http://%s:%d/internal/fetch?fileId=%s&chunkNumber=%d",
                                    sourceNode.getHost(), sourceNode.getPort(), fileId, chunkNumber);

                            Path tempFile = null;
                            try {
                                ResponseEntity<byte[]> response = restTemplate.getForEntity(fetchUrl, byte[].class);
                                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                                    byte[] chunkBytes = response.getBody();

                                    // Save the downloaded bytes temporarily on the coordinator
                                    tempFile = Files.createTempFile("replicate-heal-", ".part");
                                    Files.write(tempFile, chunkBytes);

                                    // Step 6: Upload the chunk to the new target server
                                    String storeUrl = String.format("http://%s:%d/internal/store", newTarget.getHost(), newTarget.getPort());
                                    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                                    body.add("fileId", fileId);
                                    body.add("chunkNumber", chunkNumber);
                                    body.add("file", new FileSystemResource(tempFile.toFile()));

                                    HttpHeaders headers = new HttpHeaders();
                                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                                    HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                                    restTemplate.postForEntity(storeUrl, requestEntity, String.class);

                                    // Step 7: Record the new copy's location in our SQL database
                                    ChunkPlacement newPlacement = new ChunkPlacement();
                                    newPlacement.setFileId(fileId);
                                    newPlacement.setChunkNumber(chunkNumber);
                                    newPlacement.setNodeId(newTarget.getNodeId());
                                    newPlacement.setReplicaIndex(alivePlacements.size() + healSuccessCount);
                                    chunkPlacementRepository.save(newPlacement);

                                    healSuccessCount++;
                                    meterRegistry.counter("replication.healed.chunks").increment();
                                    LOGGER.info("Successfully healed chunk {} of file {} to node {}",
                                            chunkNumber, fileId, newTarget.getNodeId());
                                } else {
                                    throw new IOException("Failed to fetch chunk content from " + sourceNode.getNodeId());
                                }
                            } catch (Exception e) {
                                LOGGER.error("Failed to heal replica for chunk {} of file {} to node {}: {}",
                                        chunkNumber, fileId, newTarget.getNodeId(), e.getMessage());
                                meterRegistry.counter("replication.failed.chunks").increment();
                            } finally {
                                if (tempFile != null) {
                                    try {
                                        Files.deleteIfExists(tempFile);
                                    } catch (IOException ioe) {
                                        LOGGER.warn("Failed to delete temp file {}: {}", tempFile, ioe.getMessage());
                                    }
                                }
                            }
                        }

                        // Step 8: Delete the old dead location records
                        chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                    } else {
                        // Node is dead, but we already have enough alive placements elsewhere, just clean up DB
                        chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                    }
                }
            }
        } finally {
            isRunning.set(false);
        }
    }
}
