package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Config.StorageProperties;
import com.dSystems.demo.Model.ChunkPlacement;
import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Repository.ChunkPlacementRepository;
import com.dSystems.demo.Service.NodeRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class ReReplicationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReReplicationScheduler.class);

    private final ChunkPlacementRepository chunkPlacementRepository;
    private final NodeRegistryService nodeRegistryService;
    private final StorageProperties storageProperties;

    public ReReplicationScheduler(ChunkPlacementRepository chunkPlacementRepository,
                                  NodeRegistryService nodeRegistryService,
                                  StorageProperties storageProperties) {
        this.chunkPlacementRepository = chunkPlacementRepository;
        this.nodeRegistryService = nodeRegistryService;
        this.storageProperties = storageProperties;
    }

    @Scheduled(fixedRate = 20000) // Every 20 seconds
    @Transactional
    public void healReplicas() {
        List<StorageNode> allNodes = nodeRegistryService.getAllNodes();
        List<StorageNode> aliveNodes = allNodes.stream().filter(n -> "ALIVE".equals(n.getStatus())).toList();
        List<StorageNode> deadNodes = allNodes.stream().filter(n -> "DEAD".equals(n.getStatus())).toList();

        if (deadNodes.isEmpty() || aliveNodes.isEmpty()) {
            return;
        }

        RestTemplate restTemplate = new RestTemplate();

        for (StorageNode deadNode : deadNodes) {
            List<ChunkPlacement> deadPlacements = chunkPlacementRepository.findByNodeId(deadNode.getNodeId());
            for (ChunkPlacement dp : deadPlacements) {
                List<ChunkPlacement> allPlacements = chunkPlacementRepository.findByFileIdAndChunkNumber(dp.getFileId(), dp.getChunkNumber());
                List<ChunkPlacement> alivePlacements = allPlacements.stream()
                        .filter(cp -> aliveNodes.stream().anyMatch(an -> an.getNodeId().equals(cp.getNodeId())))
                        .toList();

                int targetReplication = storageProperties.getReplicationFactor();
                if (alivePlacements.size() < targetReplication && !alivePlacements.isEmpty()) {
                    LOGGER.info("Chunk {} of file {} is under-replicated ({} alive, target {}). Healing...",
                            dp.getChunkNumber(), dp.getFileId(), alivePlacements.size(), targetReplication);

                    List<StorageNode> candidates = aliveNodes.stream()
                            .filter(an -> allPlacements.stream().noneMatch(cp -> cp.getNodeId().equals(an.getNodeId())))
                            .toList();

                    if (candidates.isEmpty()) {
                        LOGGER.warn("No suitable alive nodes found to host replica for chunk {} of file {}",
                                dp.getChunkNumber(), dp.getFileId());
                        continue;
                    }

                    StorageNode newTarget = candidates.get(0);
                    ChunkPlacement sourcePlacement = alivePlacements.get(0);
                    StorageNode sourceNode = aliveNodes.stream()
                            .filter(an -> an.getNodeId().equals(sourcePlacement.getNodeId()))
                            .findFirst().get();

                    String fetchUrl = String.format("http://%s:%d/internal/fetch?fileId=%s&chunkNumber=%d",
                            sourceNode.getHost(), sourceNode.getPort(), dp.getFileId(), dp.getChunkNumber());

                    try {
                        ResponseEntity<byte[]> response = restTemplate.getForEntity(fetchUrl, byte[].class);
                        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                            byte[] chunkBytes = response.getBody();

                            Path tempFile = Files.createTempFile("replicate-heal-", ".part");
                            Files.write(tempFile, chunkBytes);

                            String storeUrl = String.format("http://%s:%d/internal/store", newTarget.getHost(), newTarget.getPort());
                            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                            body.add("fileId", dp.getFileId());
                            body.add("chunkNumber", dp.getChunkNumber());
                            body.add("file", new FileSystemResource(tempFile.toFile()));

                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                            HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                            restTemplate.postForEntity(storeUrl, requestEntity, String.class);

                            Files.deleteIfExists(tempFile);

                            ChunkPlacement newPlacement = new ChunkPlacement();
                            newPlacement.setFileId(dp.getFileId());
                            newPlacement.setChunkNumber(dp.getChunkNumber());
                            newPlacement.setNodeId(newTarget.getNodeId());
                            newPlacement.setReplicaIndex(alivePlacements.size());
                            chunkPlacementRepository.save(newPlacement);

                            chunkPlacementRepository.delete(dp);
                            LOGGER.info("Successfully healed chunk {} of file {} to node {}",
                                    dp.getChunkNumber(), dp.getFileId(), newTarget.getNodeId());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to heal replica for chunk {} of file {} to node {}: {}",
                                dp.getChunkNumber(), dp.getFileId(), newTarget.getNodeId(), e.getMessage());
                    }
                }
            }
        }
    }
}
