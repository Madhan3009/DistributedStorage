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
 * Restores the replication factor after node failures. On a fixed interval, finds chunks with
 * placements on DEAD nodes, copies each under-replicated chunk from a surviving replica to other
 * ALIVE nodes, and removes the stale placement records. A file whose chunk has no surviving
 * replica is marked CORRUPT. Runs on the coordinator only.
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
     * Runs one re-replication pass. The interval is set by {@code app.healing-interval-ms}.
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
            // Step 1: Partition nodes by health.
            List<StorageNode> allNodes = nodeRegistryService.getAllNodes();
            List<StorageNode> aliveNodes = allNodes.stream().filter(n -> "ALIVE".equals(n.getStatus())).toList();
            List<StorageNode> deadNodes = allNodes.stream().filter(n -> "DEAD".equals(n.getStatus())).toList();

            // Nothing to do without a DEAD node; no possible targets without an ALIVE node.
            if (deadNodes.isEmpty() || aliveNodes.isEmpty()) {
                return;
            }

            List<String> deadNodeIds = deadNodes.stream().map(StorageNode::getNodeId).toList();
            
            // Step 2: Find files with placements on DEAD nodes.
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

                    // Separate replicas on ALIVE nodes from those on DEAD nodes.
                    List<ChunkPlacement> alivePlacements = chunkPlacements.stream()
                            .filter(cp -> aliveNodes.stream().anyMatch(an -> an.getNodeId().equals(cp.getNodeId())))
                            .toList();

                    List<ChunkPlacement> deadPlacementsForChunk = chunkPlacements.stream()
                            .filter(cp -> deadNodes.stream().anyMatch(dn -> dn.getNodeId().equals(cp.getNodeId())))
                            .toList();

                    if (deadPlacementsForChunk.isEmpty()) {
                        // No replica of this chunk was on a DEAD node.
                        continue;
                    }

                    // Step 3: Handle total loss of this chunk.
                    if (alivePlacements.isEmpty()) {
                        LOGGER.error("CRITICAL: All replicas of chunk {} for file {} ({}) are lost! Marking file as CORRUPT.",
                                chunkNumber, fileIndex.getFileName(), fileId);
                        
                        fileIndex.setStatus("CORRUPT");
                        fileIndexRepository.save(fileIndex);
                        
                        // The lost placements can never be served, so remove their records.
                        chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                        meterRegistry.counter("replication.failed.chunks").increment();
                        continue;
                    }

                    // Step 4: Re-replicate if below the target replication factor.
                    if (alivePlacements.size() < targetReplication) {
                        LOGGER.info("Chunk {} of file {} is under-replicated ({} alive, target {}). Healing...",
                                chunkNumber, fileId, alivePlacements.size(), targetReplication);

                        // Candidates are ALIVE nodes that do not already hold this chunk.
                        List<StorageNode> candidates = aliveNodes.stream()
                                .filter(an -> chunkPlacements.stream().noneMatch(cp -> cp.getNodeId().equals(an.getNodeId())))
                                .toList();

                        if (candidates.isEmpty()) {
                            LOGGER.warn("No suitable alive nodes found to host replica for chunk {} of file {}",
                                    chunkNumber, fileId);
                            // Remove the stale records even though no new replica can be placed now.
                            chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                            continue;
                        }

                        int neededReplicas = targetReplication - alivePlacements.size();
                        int healSuccessCount = 0;

                        for (int i = 0; i < Math.min(neededReplicas, candidates.size()); i++) {
                            StorageNode newTarget = candidates.get(i);
                            // Use the first surviving replica as the copy source.
                            ChunkPlacement sourcePlacement = alivePlacements.get(0);
                            
                            StorageNode sourceNode = aliveNodes.stream()
                                    .filter(an -> an.getNodeId().equals(sourcePlacement.getNodeId()))
                                    .findFirst().orElse(null);

                            if (sourceNode == null) {
                                continue;
                            }

                            // Step 5: Fetch the chunk from the source node.
                            String fetchUrl = String.format("http://%s:%d/internal/fetch?fileId=%s&chunkNumber=%d",
                                    sourceNode.getHost(), sourceNode.getPort(), fileId, chunkNumber);

                            Path tempFile = null;
                            try {
                                ResponseEntity<byte[]> response = restTemplate.getForEntity(fetchUrl, byte[].class);
                                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                                    byte[] chunkBytes = response.getBody();

                                    // Stage the chunk on the coordinator for the upload.
                                    tempFile = Files.createTempFile("replicate-heal-", ".part");
                                    Files.write(tempFile, chunkBytes);

                                    // Step 6: Store the chunk on the target node.
                                    String storeUrl = String.format("http://%s:%d/internal/store", newTarget.getHost(), newTarget.getPort());
                                    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                                    body.add("fileId", fileId);
                                    body.add("chunkNumber", chunkNumber);
                                    body.add("file", new FileSystemResource(tempFile.toFile()));

                                    HttpHeaders headers = new HttpHeaders();
                                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                                    HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                                    restTemplate.postForEntity(storeUrl, requestEntity, String.class);

                                    // Step 7: Record the new placement.
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

                        // Step 8: Remove the stale placement records.
                        chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                    } else {
                        // Already at the target replication factor; only stale records need removal.
                        chunkPlacementRepository.deleteAll(deadPlacementsForChunk);
                    }
                }
            }
        } finally {
            isRunning.set(false);
        }
    }
}
