package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.NodeRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * THE COORDINATOR'S NODE HEALTH PATROLMAN.
 * 
 * Think of this class as a background security guard running on the coordinator server.
 * Every 10 seconds, it wakes up and looks at the list of storage nodes. If it finds a server
 * marked "ALIVE" that hasn't checked in (sent a heartbeat) for more than 30 seconds,
 * it assumes the server has crashed or disconnected, and marks it "DEAD".
 * 
 * Annotations:
 * - @Component: Registers this class as a managed background tool.
 */
@Component
public class HeartbeatScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatScheduler.class);
    
    // The maximum time (in seconds) we are willing to wait for a check-in before declaring a node dead.
    private static final int MISSING_HEARTBEAT_THRESHOLD_SECONDS = 30;

    private final NodeRegistryService nodeRegistryService;

    /**
     * Constructor - receives the registry assistant.
     */
    public HeartbeatScheduler(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    /**
     * The health patrol check method.
     * 
     * - @Scheduled(fixedRate = 10000): Tells Spring Boot to automatically run this method 
     *   in the background every 10 seconds (10,000 milliseconds).
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void checkNodeHealth() {
        // Step 1: Grab all registered servers from the database
        List<StorageNode> allNodes = nodeRegistryService.getAllNodes();
        
        // Step 2: Calculate the cutoff time (30 seconds ago)
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(MISSING_HEARTBEAT_THRESHOLD_SECONDS);

        // Step 3: Inspect each server's last check-in timestamp
        for (StorageNode node : allNodes) {
            // If the server is supposed to be ALIVE or SUSPECTED, but its last check-in was before the cutoff:
            if (("ALIVE".equals(node.getStatus()) || "SUSPECTED".equals(node.getStatus())) 
                    && node.getLastHeartbeatAt().isBefore(threshold)) {
                LOGGER.warn("Node {} has not sent heartbeat since {}. Incrementing missed heartbeats.", 
                        node.getNodeId(), node.getLastHeartbeatAt());
                nodeRegistryService.handleMissedHeartbeat(node.getNodeId());
            }
        }
    }
}
