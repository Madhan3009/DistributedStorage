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

/**
 * Maintains the registry of storage nodes: registration, heartbeat processing, and health status
 * transitions (ALIVE, SUSPECTED, DEAD).
 */
@Service
public class NodeRegistryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRegistryService.class);

    private final StorageNodeRepository storageNodeRepository;

    public NodeRegistryService(StorageNodeRepository storageNodeRepository) {
        this.storageNodeRepository = storageNodeRepository;
    }

    /**
     * Registers a new node or updates the address of a known node, and marks it ALIVE.
     *
     * @param nodeId unique node identifier
     * @param host hostname or IP address of the node
     * @param port port on which the node accepts requests
     */
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
        node.setMissedHeartbeats(0);
        node.setLastHeartbeatAt(LocalDateTime.now());
        
        return storageNodeRepository.save(node);
    }

    /**
    /**
     * Records a heartbeat, marking the node ALIVE and updating its disk usage.
     *
     * @param nodeId unique node identifier
     * @param diskUsedBytes disk space in use, in bytes
     * @param diskFreeBytes free disk space, in bytes
     * @return true if the node is registered, false otherwise
     */
    @Transactional
    public boolean processHeartbeat(String nodeId, long diskUsedBytes, long diskFreeBytes) {
        Optional<StorageNode> nodeOpt = storageNodeRepository.findByNodeId(nodeId);
        if (nodeOpt.isPresent()) {
            StorageNode node = nodeOpt.get();
            node.setLastHeartbeatAt(LocalDateTime.now());
            node.setStatus("ALIVE");
            node.setMissedHeartbeats(0);
            node.setDiskUsedBytes(diskUsedBytes);
            node.setDiskFreeBytes(diskFreeBytes);
            storageNodeRepository.save(node);
            return true;
        }
        return false;
    }

    /**
     * Records a missed heartbeat. The first miss marks the node SUSPECTED; the third marks it DEAD.
     */
    @Transactional
    public void handleMissedHeartbeat(String nodeId) {
        storageNodeRepository.findByNodeId(nodeId).ifPresent(node -> {
            int missed = node.getMissedHeartbeats() + 1;
            node.setMissedHeartbeats(missed);
            if (missed == 1) {
                node.setStatus("SUSPECTED");
                storageNodeRepository.save(node);
                LOGGER.warn("Node {} missed 1 heartbeat. Marking SUSPECTED.", nodeId);
            } else if (missed >= 3) {
                if (!"DEAD".equals(node.getStatus())) {
                    node.setStatus("DEAD");
                    storageNodeRepository.save(node);
                    LOGGER.error("Node {} missed {} heartbeats. Marking DEAD.", nodeId, missed);
                }
            } else {
                storageNodeRepository.save(node);
            }
        });
    }

    /**
     * Immediately marks a node DEAD, regardless of its missed heartbeat count. Used to simulate
     * node failures.
     */
    @Transactional
    public void markNodeDead(String nodeId) {
        storageNodeRepository.findByNodeId(nodeId).ifPresent(node -> {
            if (!"DEAD".equals(node.getStatus())) {
                node.setStatus("DEAD");
                node.setMissedHeartbeats(3);
                storageNodeRepository.save(node);
                LOGGER.warn("Node {} has been marked DEAD due to missed heartbeats", nodeId);
            }
        });
    }

    /**
     * @return all nodes currently marked ALIVE
     */
    public List<StorageNode> getAliveNodes() {
        return storageNodeRepository.findByStatus("ALIVE");
    }

    /**
     * @return all registered nodes, regardless of status
     */
    public List<StorageNode> getAllNodes() {
        return storageNodeRepository.findAll();
    }
}
