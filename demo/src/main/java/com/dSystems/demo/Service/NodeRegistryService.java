package com.dSystems.demo.Service;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Repository.StorageNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NodeRegistryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRegistryService.class);

    private final StorageNodeRepository storageNodeRepository;

    public NodeRegistryService(StorageNodeRepository storageNodeRepository) {
        this.storageNodeRepository = storageNodeRepository;
    }

    @Transactional
    public StorageNode registerNode(String nodeId, String host, int port) {
        Optional<StorageNode> existing = storageNodeRepository.findByNodeId(nodeId);
        StorageNode node;
        if (existing.isPresent()) {
            node = existing.get();
            node.setHost(host);
            node.setPort(port);
            LOGGER.info("Updating existing storage node registration: {}", nodeId);
        } else {
            node = new StorageNode();
            node.setNodeId(nodeId);
            node.setHost(host);
            node.setPort(port);
            LOGGER.info("Registering new storage node: {}", nodeId);
        }
        node.setStatus("ALIVE");
        node.setLastHeartbeatAt(LocalDateTime.now());
        return storageNodeRepository.save(node);
    }

    @Transactional
    public boolean processHeartbeat(String nodeId, long diskUsedBytes, long diskFreeBytes) {
        Optional<StorageNode> nodeOpt = storageNodeRepository.findByNodeId(nodeId);
        if (nodeOpt.isPresent()) {
            StorageNode node = nodeOpt.get();
            node.setLastHeartbeatAt(LocalDateTime.now());
            node.setStatus("ALIVE");
            node.setDiskUsedBytes(diskUsedBytes);
            node.setDiskFreeBytes(diskFreeBytes);
            storageNodeRepository.save(node);
            return true;
        }
        return false;
    }

    @Transactional
    public void markNodeDead(String nodeId) {
        storageNodeRepository.findByNodeId(nodeId).ifPresent(node -> {
            if (!"DEAD".equals(node.getStatus())) {
                node.setStatus("DEAD");
                storageNodeRepository.save(node);
                LOGGER.warn("Node {} has been marked DEAD due to missed heartbeats", nodeId);
                // Future extension: trigger re-replication event
            }
        });
    }

    public List<StorageNode> getAliveNodes() {
        return storageNodeRepository.findByStatus("ALIVE");
    }

    public List<StorageNode> getAllNodes() {
        return storageNodeRepository.findAll();
    }
}
