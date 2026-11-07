package com.dSystems.demo;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.ConsistentHashRing;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link com.dSystems.demo.Service.ConsistentHashRing}: distinct replica placement,
 * deterministic lookups, and rerouting when a node is removed.
 */
class ConsistentHashRingTest {

    @Test
    void testConsistentHashingDistributionAndReplication() {
        ConsistentHashRing ring = new ConsistentHashRing();

        // Step 1: Create three storage nodes.
        List<StorageNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            StorageNode node = new StorageNode();
            node.setNodeId("node-" + i);
            node.setHost("localhost");
            node.setPort(9000 + i);
            node.setStatus("ALIVE");
            nodes.add(node);
        }

        // Step 2: Look up placements with a replication factor of 2.
        String key = "testfile-chunk-0";
        List<StorageNode> targets = ring.getNodesForKey(key, 2, nodes);

        // The two targets must be distinct nodes.
        assertEquals(2, targets.size());
        assertNotEquals(targets.get(0).getNodeId(), targets.get(1).getNodeId());

        // Step 3: The same lookup must return the same nodes in the same order.
        List<StorageNode> targets2 = ring.getNodesForKey(key, 2, nodes);
        assertEquals(targets.get(0).getNodeId(), targets2.get(0).getNodeId());
        assertEquals(targets.get(1).getNodeId(), targets2.get(1).getNodeId());

        // Step 4: Remove node-2 from the eligible nodes.
        nodes.removeIf(n -> n.getNodeId().equals("node-2"));
        
        List<StorageNode> targetsAfterFailure = ring.getNodesForKey(key, 2, nodes);

        // Both replicas must be placed on the remaining nodes.
        assertEquals(2, targetsAfterFailure.size());
        assertFalse(targetsAfterFailure.stream().anyMatch(n -> n.getNodeId().equals("node-2")));
    }
}
