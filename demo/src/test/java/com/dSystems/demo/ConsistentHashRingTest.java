package com.dSystems.demo;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.ConsistentHashRing;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * THE CONSISTENT HASH RING TEST (THE TRAFFIC WHEEL INSPECTOR).
 * 
 * Think of this class as a fast-running check for our consistent hash ring algorithms.
 * It is a pure unit test, meaning it doesn't need database files, network configs, or active servers.
 * It sets up mock data in memory and verifies that:
 * 1. Chunks are mapped to unique servers.
 * 2. The mapping is deterministic (the same input always gets routed to the exact same servers).
 * 3. If a server dies, the system successfully reroutes to other healthy servers.
 */
class ConsistentHashRingTest {

    /**
     * Test Case: Verifies mapping, replication, consistency, and failover behavior.
     */
    @Test
    void testConsistentHashingDistributionAndReplication() {
        ConsistentHashRing ring = new ConsistentHashRing();

        // Step 1: Set up 3 mock storage servers in memory
        List<StorageNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            StorageNode node = new StorageNode();
            node.setNodeId("node-" + i);
            node.setHost("localhost");
            node.setPort(9000 + i);
            node.setStatus("ALIVE");
            nodes.add(node);
        }

        // Step 2: Ask the ring where to place "testfile-chunk-0" with 2 backups (replication factor = 2)
        String key = "testfile-chunk-0";
        List<StorageNode> targets = ring.getNodesForKey(key, 2, nodes);

        // Verify: We got exactly 2 targets, and they are unique servers (not the same server twice)
        assertEquals(2, targets.size());
        assertNotEquals(targets.get(0).getNodeId(), targets.get(1).getNodeId());

        // Step 3: Ask the ring for the same chunk again.
        // It must yield the exact same servers in the exact same order (consistency).
        List<StorageNode> targets2 = ring.getNodesForKey(key, 2, nodes);
        assertEquals(targets.get(0).getNodeId(), targets2.get(0).getNodeId());
        assertEquals(targets.get(1).getNodeId(), targets2.get(1).getNodeId());

        // Step 4: Simulate "node-2" failing/going offline
        nodes.removeIf(n -> n.getNodeId().equals("node-2"));
        
        // Ask the ring where to route the chunk now
        List<StorageNode> targetsAfterFailure = ring.getNodesForKey(key, 2, nodes);

        // Verify: We still get 2 healthy backups, and neither of them is the dead "node-2"
        assertEquals(2, targetsAfterFailure.size());
        assertFalse(targetsAfterFailure.stream().anyMatch(n -> n.getNodeId().equals("node-2")));
    }
}
