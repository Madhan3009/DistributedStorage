package com.dSystems.demo;

import com.dSystems.demo.Config.StorageProperties;
import com.dSystems.demo.Model.ChunkPlacement;
import com.dSystems.demo.Model.FileIndex;
import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Repository.ChunkPlacementRepository;
import com.dSystems.demo.Repository.FileIndexRepository;
import com.dSystems.demo.Scheduler.ReReplicationScheduler;
import com.dSystems.demo.Service.NodeRegistryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReReplicationSchedulerTest {

    private ChunkPlacementRepository chunkPlacementRepository;
    private NodeRegistryService nodeRegistryService;
    private StorageProperties storageProperties;
    private FileIndexRepository fileIndexRepository;
    private RestTemplate restTemplate;
    private MeterRegistry meterRegistry;
    private Counter mockCounter;

    private ReReplicationScheduler scheduler;

    @BeforeEach
    void setUp() {
        chunkPlacementRepository = mock(ChunkPlacementRepository.class);
        nodeRegistryService = mock(NodeRegistryService.class);
        storageProperties = mock(StorageProperties.class);
        fileIndexRepository = mock(FileIndexRepository.class);
        restTemplate = mock(RestTemplate.class);
        meterRegistry = mock(MeterRegistry.class);
        mockCounter = mock(Counter.class);

        when(meterRegistry.counter(anyString())).thenReturn(mockCounter);

        scheduler = new ReReplicationScheduler(
                chunkPlacementRepository,
                nodeRegistryService,
                storageProperties,
                fileIndexRepository,
                restTemplate,
                meterRegistry
        );
    }

    @Test
    void whenNoDeadNodes_thenNoHealingAttempted() {
        // Arrange
        StorageNode aliveNode = new StorageNode();
        aliveNode.setNodeId("node-1");
        aliveNode.setStatus("ALIVE");
        when(nodeRegistryService.getAllNodes()).thenReturn(List.of(aliveNode));

        // Act
        scheduler.healReplicas();

        // Assert
        verify(chunkPlacementRepository, never()).findFileIdsWithPlacementsOnNodes(anyList());
    }

    @Test
    void whenUnderReplicated_thenNewReplicaCreated() {
        // Arrange
        StorageNode aliveNode = new StorageNode();
        aliveNode.setNodeId("node-2");
        aliveNode.setStatus("ALIVE");
        aliveNode.setHost("localhost");
        aliveNode.setPort(9002);

        StorageNode anotherAliveNode = new StorageNode();
        anotherAliveNode.setNodeId("node-3");
        anotherAliveNode.setStatus("ALIVE");
        anotherAliveNode.setHost("localhost");
        anotherAliveNode.setPort(9003);

        StorageNode deadNode = new StorageNode();
        deadNode.setNodeId("node-1");
        deadNode.setStatus("DEAD");

        when(nodeRegistryService.getAllNodes()).thenReturn(List.of(aliveNode, anotherAliveNode, deadNode));
        when(storageProperties.getReplicationFactor()).thenReturn(2);

        String fileId = "test-file-123";
        when(chunkPlacementRepository.findFileIdsWithPlacementsOnNodes(List.of("node-1")))
                .thenReturn(List.of(fileId));

        FileIndex index = new FileIndex();
        index.setFileId(fileId);
        index.setFileName("test.txt");
        index.setTotalChunks(1);
        when(fileIndexRepository.findByFileId(fileId)).thenReturn(Optional.of(index));

        ChunkPlacement alivePlacement = new ChunkPlacement();
        alivePlacement.setFileId(fileId);
        alivePlacement.setChunkNumber(0);
        alivePlacement.setNodeId("node-2");
        alivePlacement.setReplicaIndex(0);

        ChunkPlacement deadPlacement = new ChunkPlacement();
        deadPlacement.setFileId(fileId);
        deadPlacement.setChunkNumber(0);
        deadPlacement.setNodeId("node-1");
        deadPlacement.setReplicaIndex(1);

        when(chunkPlacementRepository.findByFileId(fileId))
                .thenReturn(List.of(alivePlacement, deadPlacement));

        byte[] dummyBytes = "hello".getBytes();
        when(restTemplate.getForEntity(contains("/internal/fetch"), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(dummyBytes, HttpStatus.OK));

        // Act
        scheduler.healReplicas();

        // Assert
        verify(chunkPlacementRepository, times(1)).save(argThat(cp -> 
                cp.getFileId().equals(fileId) &&
                cp.getChunkNumber() == 0 &&
                cp.getNodeId().equals("node-3")
        ));
        verify(chunkPlacementRepository, times(1)).deleteAll(anyList());
        verify(mockCounter, atLeastOnce()).increment();
    }

    @Test
    void whenAllReplicasDead_thenFileMarkedCorrupt() {
        // Arrange
        StorageNode deadNode = new StorageNode();
        deadNode.setNodeId("node-1");
        deadNode.setStatus("DEAD");

        StorageNode aliveNode = new StorageNode();
        aliveNode.setNodeId("node-2");
        aliveNode.setStatus("ALIVE");

        when(nodeRegistryService.getAllNodes()).thenReturn(List.of(aliveNode, deadNode));

        String fileId = "test-file-123";
        when(chunkPlacementRepository.findFileIdsWithPlacementsOnNodes(List.of("node-1")))
                .thenReturn(List.of(fileId));

        FileIndex index = new FileIndex();
        index.setFileId(fileId);
        index.setFileName("test.txt");
        index.setTotalChunks(1);
        index.setStatus("COMPLETE");
        when(fileIndexRepository.findByFileId(fileId)).thenReturn(Optional.of(index));

        ChunkPlacement deadPlacement = new ChunkPlacement();
        deadPlacement.setFileId(fileId);
        deadPlacement.setChunkNumber(0);
        deadPlacement.setNodeId("node-1");
        deadPlacement.setReplicaIndex(0);

        when(chunkPlacementRepository.findByFileId(fileId))
                .thenReturn(List.of(deadPlacement));

        // Act
        scheduler.healReplicas();

        // Assert
        assertEquals("CORRUPT", index.getStatus());
        verify(fileIndexRepository, times(1)).save(index);
        verify(chunkPlacementRepository, times(1)).deleteAll(anyList());
        verify(mockCounter, times(1)).increment();
    }
}
