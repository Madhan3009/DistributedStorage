package com.dSystems.demo;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.ConsistentHashRing;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    @Test
    void testConsistentHashingDistributionAndReplication() {
        ConsistentHashRing ring = new ConsistentHashRing();

        // Create 3 mock storage nodes
        List<StorageNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            StorageNode node = new StorageNode();
            node.setNodeId("node-" + i);
            node.setHost("localhost");
            node.setPort(9000 + i);
            node.setStatus("ALIVE");
            nodes.add(node);
        }

        // Test chunk key placement with replication factor = 2
        String key = "testfile-chunk-0";
        List<StorageNode> targets = ring.getNodesForKey(key, 2, nodes);

        assertEquals(2, targets.size());
        assertNotEquals(targets.get(0).getNodeId(), targets.get(1).getNodeId());

        // Test consistent routing (same key should yield same target list)
        List<StorageNode> targets2 = ring.getNodesForKey(key, 2, nodes);
        assertEquals(targets.get(0).getNodeId(), targets2.get(0).getNodeId());
        assertEquals(targets.get(1).getNodeId(), targets2.get(1).getNodeId());

        // Now mock node-2 going down
        nodes.removeIf(n -> n.getNodeId().equals("node-2"));
        List<StorageNode> targetsAfterFailure = ring.getNodesForKey(key, 2, nodes);

        // Should still yield up to 2 unique nodes if available (we have 2 remaining)
        assertEquals(2, targetsAfterFailure.size());
        assertFalse(targetsAfterFailure.stream().anyMatch(n -> n.getNodeId().equals("node-2")));
    }
}
