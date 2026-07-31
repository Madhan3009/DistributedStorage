package com.dSystems.demo.Service;

import com.dSystems.demo.Model.StorageNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * THE TRAFFIC DIRECTORS / BALANCING WHEEL.
 * 
 * Think of this class as a virtual "Spinning Wheel" (or clock face) used to decide which servers
 * should store which file chunks, ensuring that files are spread out evenly.
 * 
 * Concept - Consistent Hashing:
 * 1. Imagine a big circle with numbers from 0 to 9,000,000,000.
 * 2. We convert each server's name into a number (hash) and place the server at that spot on the circle.
 * 3. We convert a file chunk's name into a number (hash) and place it on the circle.
 * 4. To store the chunk, we start at its spot on the circle and walk clockwise until we hit the first server.
 *    That server gets the chunk!
 * 5. If we need backups (replication), we keep walking clockwise to find the next unique servers.
 * 
 * Why Virtual Nodes (VIRTUAL_NODES_COUNT = 50)?
 * - If we only place each server on the circle once, one server might end up with a huge sector of the circle
 *   and get overloaded.
 * - By placing 50 virtual copies of each server on the circle (using names like "node-1-vn-0", "node-1-vn-1"),
 *   we break the circle into tiny, interleaved pieces, which spreads the storage load extremely evenly.
 */
@Service
public class ConsistentHashRing {

    // Number of virtual spots each physical server gets on the circle.
    private static final int VIRTUAL_NODES_COUNT = 50;

    /**
     * Converts a text string (like a chunk ID or server name) into a 64-bit number (long)
     * using the MD5 cryptographic formula. This number represents a point on our circle.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            // Take the first 8 bytes of the MD5 signature and combine them into a single number.
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
    }

    /**
     * Looks up the best servers to store a chunk of data.
     * 
     * @param key The chunk identifier (e.g. "myFile-chunk-0").
     * @param replicationFactor How many copies (replicas) of the chunk we want to store.
     * @param aliveNodes The list of storage servers currently online.
     * @return A list of unique storage servers assigned to store this chunk.
     */
    public List<StorageNode> getNodesForKey(String key, int replicationFactor, List<StorageNode> aliveNodes) {
        if (aliveNodes == null || aliveNodes.isEmpty()) {
            return Collections.emptyList();
        }

        // TreeMap keeps its entries sorted. This acts as our sorted "circle".
        TreeMap<Long, StorageNode> ring = new TreeMap<>();
        
        // Step 1: Map all online servers to multiple virtual spots on the circle.
        for (StorageNode node : aliveNodes) {
            for (int i = 0; i < VIRTUAL_NODES_COUNT; i++) {
                ring.put(hash(node.getNodeId() + "-vn-" + i), node);
            }
        }

        List<StorageNode> selectedNodes = new ArrayList<>();
        // Step 2: Hash our chunk's key to find its spot on the circle.
        long hashVal = hash(key);

        // Step 3: tailMap gets all items on the circle starting *at* or *after* our chunk's position (clockwise).
        SortedMap<Long, StorageNode> tailMap = ring.tailMap(hashVal);
        Iterator<StorageNode> iterator = tailMap.values().iterator();

        int targetSize = Math.min(replicationFactor, aliveNodes.size());

        // Step 4: Walk clockwise around the circle to select unique servers until we reach our target replica count.
        while (selectedNodes.size() < targetSize) {
            // If we walk all the way to the end of the circle, loop back to the very beginning (12 o'clock).
            if (!iterator.hasNext()) {
                iterator = ring.values().iterator();
            }
            if (!iterator.hasNext()) {
                break;
            }
            StorageNode candidate = iterator.next();
            
            // Ensure we don't pick the same physical server twice for the same chunk!
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
