package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@Component
public class NodeHeartbeatSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeHeartbeatSender.class);

    private final StorageProperties storageProperties;
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

    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        if (!isNodeRole) {
            return;
        }

        String coordinatorUrl = storageProperties.getCoordinatorUrl();
        String registerUrl = coordinatorUrl + "/nodes/register";
        String heartbeatUrl = coordinatorUrl + "/nodes/heartbeat";

        RestTemplate restTemplate = new RestTemplate();

        // 1. Register/Refresh Registration
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

        // 2. Send Heartbeat with Disk Info
        try {
            File file = new File(storageProperties.getTempDir());
            // Create directory if not exists
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
