package com.dSystems.demo.Scheduler;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.NodeRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class HeartbeatScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private static final int MISSING_HEARTBEAT_THRESHOLD_SECONDS = 30;

    private final NodeRegistryService nodeRegistryService;

    public HeartbeatScheduler(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void checkNodeHealth() {
        List<StorageNode> allNodes = nodeRegistryService.getAllNodes();
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(MISSING_HEARTBEAT_THRESHOLD_SECONDS);

        for (StorageNode node : allNodes) {
            if ("ALIVE".equals(node.getStatus()) && node.getLastHeartbeatAt().isBefore(threshold)) {
                LOGGER.warn("Node {} has not sent heartbeat since {}. Marking DEAD.", 
                        node.getNodeId(), node.getLastHeartbeatAt());
                nodeRegistryService.markNodeDead(node.getNodeId());
            }
        }
    }
}
