package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;

/**
 * THE WORKER NODE'S CHECK-IN SENDER.
 * 
 * Think of this class as a background transmitter running on every worker storage server. 
 * Every 10 seconds, if this application is configured to run as a worker "NODE" (rather than
 * the central coordinator), it wakes up and sends two network messages to the central coordinator:
 * 1. "I am here! Register my address and port."
 * 2. "Here is my current disk space usage status."
 * 
 * Annotations:
 * - @Component: Registers this class as a managed background tool.
 */
@Component
public class NodeHeartbeatSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeHeartbeatSender.class);

    private final StorageProperties storageProperties;
    
    // Flag to determine if this application instance is playing the "Worker Node" role.
    private final boolean isNodeRole;

    /**
     * Constructor - Detects the application role on startup.
     */
    public NodeHeartbeatSender(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.isNodeRole = "NODE".equalsIgnoreCase(storageProperties.getRole());
        
        // Print role confirmation to the logs
        if (this.isNodeRole) {
            LOGGER.info("Starting application in storage NODE role: {}", storageProperties.getNodeId());
        } else {
            LOGGER.info("Starting application in COORDINATOR role");
        }
    }

    /**
     * Wakes up every 10 seconds to check in with the coordinator.
     * 
     * - @Scheduled(fixedRate = 10000): Spring Boot scheduler runs this in the background 
     *   every 10 seconds (10,000 milliseconds).
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        // If we are the Coordinator, we do not need to send heartbeats to ourselves!
        if (!isNodeRole) {
            return;
        }

        // Get the Coordinator's web URLs from configuration settings
        String coordinatorUrl = storageProperties.getCoordinatorUrl();
        String registerUrl = coordinatorUrl + "/nodes/register";
        String heartbeatUrl = coordinatorUrl + "/nodes/heartbeat";

        RestTemplate restTemplate = new RestTemplate(); // Network HTTP helper

        // --- STEP 1: REGISTER WITH COORDINATOR ---
        try {
            // Pack parameters: our node name, host address, and listening port
            String registerParams = String.format("?nodeId=%s&host=%s&port=%d",
                    storageProperties.getNodeId(),
                    storageProperties.getHost(),
                    storageProperties.getPort());
            
            // Send the network registration request
            restTemplate.postForEntity(registerUrl + registerParams, null, String.class);
            LOGGER.debug("Node registered successfully with coordinator: {}", storageProperties.getNodeId());
        } catch (Exception e) {
            LOGGER.error("Failed to register node with coordinator: {}", e.getMessage());
        }

        // --- STEP 2: MEASURE DISK SPACE AND SEND HEARTBEAT ---
        try {
            File file = new File(storageProperties.getTempDir());
            // Create the local temp folder if it doesn't exist yet
            if (!file.exists()) {
                file.mkdirs();
            }
            
            // Calculate storage metrics on the hard drive
            long totalSpace = file.getTotalSpace();
            long freeSpace = file.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;

            // Pack parameters: our node name, used space, and free space in bytes
            String heartbeatParams = String.format("?nodeId=%s&diskUsed=%d&diskFree=%d",
                    storageProperties.getNodeId(),
                    usedSpace,
                    freeSpace);
            
            // Send the network heartbeat request
            restTemplate.postForEntity(heartbeatUrl + heartbeatParams, null, String.class);
            LOGGER.info("Heartbeat sent successfully from node: {} (used: {} MB, free: {} MB)",
                    storageProperties.getNodeId(), usedSpace / (1024 * 1024), freeSpace / (1024 * 1024));
        } catch (Exception e) {
            LOGGER.error("Failed to send heartbeat to coordinator: {}", e.getMessage());
        }
    }
}
