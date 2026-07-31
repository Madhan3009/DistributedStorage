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
 * THE SERVER REGISTRY MANAGER / DESK.
 * 
 * Think of this service as the "HR Department" for our cluster of storage servers.
 * It manages:
 * 1. When a new storage server joins, registering its address and network details.
 * 2. Processing regular "I am alive" check-in signals (heartbeats) from each server, 
 *    along with their current disk space stats.
 * 3. Marking a server as "DEAD" if it fails to check in for too long, so we stop sending files to it.
 * 
 * Annotations:
 * - @Transactional: Ensures that database operations within a method either succeed completely
 *   or roll back entirely in case of an error (leaving the database in a clean state).
 */
@Service
public class NodeRegistryService {
    // Logger tool to output server state events to the console log.
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRegistryService.class);

    // Database helper to save/load storage server records
    private final StorageNodeRepository storageNodeRepository;

    /**
     * Constructor - Receives the database repository.
     */
    public NodeRegistryService(StorageNodeRepository storageNodeRepository) {
        this.storageNodeRepository = storageNodeRepository;
    }

    /**
     * Registers a storage server when it starts up and connects to the coordinator.
     * 
     * @param nodeId Unique name of the server (e.g. "node-1").
     * @param host IP address or hostname of the server.
     * @param port Network port number the server is listening on.
     */
    @Transactional
    public StorageNode registerNode(String nodeId, String host, int port) {
        Optional<StorageNode> existing = storageNodeRepository.findByNodeId(nodeId);
        StorageNode node;
        
        if (existing.isPresent()) {
            // Server was already registered, update its network details
            node = existing.get();
            node.setHost(host);
            node.setPort(port);
            LOGGER.info("Updating existing storage node registration: {}", nodeId);
        } else {
            // Brand new server, create a fresh profile record
            node = new StorageNode();
            node.setNodeId(nodeId);
            node.setHost(host);
            node.setPort(port);
            LOGGER.info("Registering new storage node: {}", nodeId);
        }
        
        // Mark it as online and record check-in time
        node.setStatus("ALIVE");
        node.setMissedHeartbeats(0);
        node.setLastHeartbeatAt(LocalDateTime.now());
        
        // Save back to the database
        return storageNodeRepository.save(node);
    }

    /**
     * Processes a periodic heartbeat signal sent from a storage node.
     * 
     * @param nodeId Unique name of the server checking in.
     * @param diskUsedBytes Current byte space occupied by files on that server.
     * @param diskFreeBytes Remaining empty bytes left on that server.
     * @return true if we found and updated the server, false if the server is unknown.
     */
    @Transactional
    public boolean processHeartbeat(String nodeId, long diskUsedBytes, long diskFreeBytes) {
        Optional<StorageNode> nodeOpt = storageNodeRepository.findByNodeId(nodeId);
        if (nodeOpt.isPresent()) {
            StorageNode node = nodeOpt.get();
            node.setLastHeartbeatAt(LocalDateTime.now()); // Update last check-in time
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
     * Increments missed heartbeats counter and updates state (ALIVE -> SUSPECTED -> DEAD).
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
     * Marks a server as dead when it has been silent for too long.
     */
    @Transactional
    public void markNodeDead(String nodeId) {
        storageNodeRepository.findByNodeId(nodeId).ifPresent(node -> {
            // Only update status and log if it wasn't already marked DEAD
            if (!"DEAD".equals(node.getStatus())) {
                node.setStatus("DEAD");
                node.setMissedHeartbeats(3);
                storageNodeRepository.save(node);
                LOGGER.warn("Node {} has been marked DEAD due to missed heartbeats", nodeId);
            }
        });
    }

    /**
     * Lists all servers that are currently marked "ALIVE".
     */
    public List<StorageNode> getAliveNodes() {
        return storageNodeRepository.findByStatus("ALIVE");
    }

    /**
     * Lists every server in the registry, including dead ones.
     */
    public List<StorageNode> getAllNodes() {
        return storageNodeRepository.findAll();
    }
}
