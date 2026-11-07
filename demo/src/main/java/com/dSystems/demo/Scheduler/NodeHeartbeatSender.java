package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;

/**
 * Reports a storage node's status to the coordinator. Every 10 seconds, instances running in the
 * NODE role re-register and send a heartbeat with current disk usage. Re-registering on each cycle
 * lets a node recover automatically if the coordinator restarts. No-op in the COORDINATOR role.
 */
@Component
public class NodeHeartbeatSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeHeartbeatSender.class);

    private final StorageProperties storageProperties;
    
    // True when this instance runs in the NODE role.
    private final boolean isNodeRole;

    public NodeHeartbeatSender(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.isNodeRole = "NODE".equalsIgnoreCase(storageProperties.getRole());
        
        if (this.isNodeRole) {
            LOGGER.info("Starting application in storage NODE role: {}", storageProperties.getNodeId());
        } else {
            LOGGER.info("Starting application in COORDINATOR role");
        }
    }

    /**
     * Registers this node with the coordinator, then sends a heartbeat. The two calls fail
     * independently; failures are logged and retried on the next cycle.
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        if (!isNodeRole) {
            return;
        }

        String coordinatorUrl = storageProperties.getCoordinatorUrl();
        String registerUrl = coordinatorUrl + "/nodes/register";
        String heartbeatUrl = coordinatorUrl + "/nodes/heartbeat";

        RestTemplate restTemplate = new RestTemplate();

        // Register with the coordinator.
        try {
            String registerParams = String.format("?nodeId=%s&host=%s&port=%d",
                    storageProperties.getNodeId(),
                    storageProperties.getHost(),
                    storageProperties.getPort());
            
            restTemplate.postForEntity(registerUrl + registerParams, null, String.class);
            LOGGER.debug("Node registered successfully with coordinator: {}", storageProperties.getNodeId());
        } catch (Exception e) {
            LOGGER.error("Failed to register node with coordinator: {}", e.getMessage());
        }

        // Measure disk usage and send the heartbeat.
        try {
            File file = new File(storageProperties.getTempDir());
            if (!file.exists()) {
                file.mkdirs();
            }
            
            long totalSpace = file.getTotalSpace();
            long freeSpace = file.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;

            String heartbeatParams = String.format("?nodeId=%s&diskUsed=%d&diskFree=%d",
                    storageProperties.getNodeId(),
                    usedSpace,
                    freeSpace);
            
            restTemplate.postForEntity(heartbeatUrl + heartbeatParams, null, String.class);
            LOGGER.info("Heartbeat sent successfully from node: {} (used: {} MB, free: {} MB)",
                    storageProperties.getNodeId(), usedSpace / (1024 * 1024), freeSpace / (1024 * 1024));
        } catch (Exception e) {
            LOGGER.error("Failed to send heartbeat to coordinator: {}", e.getMessage());
        }
    }
}
