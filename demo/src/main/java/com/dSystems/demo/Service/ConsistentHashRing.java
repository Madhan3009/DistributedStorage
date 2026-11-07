package com.dSystems.demo.Service;

import com.dSystems.demo.Model.StorageNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Selects storage nodes for chunk replicas using consistent hashing.
 *
 * <p>Nodes and chunk keys are hashed onto the 64-bit ring. A chunk's replicas are placed on the
 * first distinct physical nodes found moving clockwise from the key's position, wrapping around at
 * the end. Each physical node occupies {@value #VIRTUAL_NODES_COUNT} virtual positions to spread
 * keys more evenly and limit the data remapped when cluster membership changes.
 *
 * <p>The ring is rebuilt from the supplied node list on each lookup.
 */
@Service
public class ConsistentHashRing {

    // Virtual positions per physical node.
    private static final int VIRTUAL_NODES_COUNT = 50;

    /**
     * Hashes a key to a ring position using the first 8 bytes of its MD5 digest. MD5 is used for
     * key distribution only, not for security.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
    }

    /**
     * Selects the nodes that should hold a chunk's replicas.
     *
     * @param key chunk key, e.g. {@code myFile-chunk-0}
     * @param replicationFactor desired number of replicas
     * @param aliveNodes nodes eligible for placement
     * @return distinct nodes in ring order; fewer than requested if too few nodes are available
     */
    public List<StorageNode> getNodesForKey(String key, int replicationFactor, List<StorageNode> aliveNodes) {
        if (aliveNodes == null || aliveNodes.isEmpty()) {
            return Collections.emptyList();
        }

        // Sorted by position, so that tailMap can locate the clockwise successors of a key.
        TreeMap<Long, StorageNode> ring = new TreeMap<>();
        
        for (StorageNode node : aliveNodes) {
            for (int i = 0; i < VIRTUAL_NODES_COUNT; i++) {
                ring.put(hash(node.getNodeId() + "-vn-" + i), node);
            }
        }

        List<StorageNode> selectedNodes = new ArrayList<>();
        long hashVal = hash(key);

        SortedMap<Long, StorageNode> tailMap = ring.tailMap(hashVal);
        Iterator<StorageNode> iterator = tailMap.values().iterator();

        int targetSize = Math.min(replicationFactor, aliveNodes.size());

        // Walk clockwise until enough distinct physical nodes have been selected.
        while (selectedNodes.size() < targetSize) {
            // Wrap around to the start of the ring.
            if (!iterator.hasNext()) {
                iterator = ring.values().iterator();
            }
            if (!iterator.hasNext()) {
                break;
            }
            StorageNode candidate = iterator.next();
            
            // Virtual nodes can repeat a physical node; each node may hold only one replica.
            boolean alreadySelected = false;
            for (StorageNode selected : selectedNodes) {
                if (selected.getNodeId().equals(candidate.getNodeId())) {
                    alreadySelected = true;
                    break;
                }
            }
            if (!alreadySelected) {
                selectedNodes.add(candidate);
            }
        }

        return selectedNodes;
    }
}
