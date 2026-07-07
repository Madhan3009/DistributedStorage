package com.dSystems.demo.Service;

import com.dSystems.demo.Model.StorageNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ConsistentHashRing {

    private static final int VIRTUAL_NODES_COUNT = 50;

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

    public List<StorageNode> getNodesForKey(String key, int replicationFactor, List<StorageNode> aliveNodes) {
        if (aliveNodes == null || aliveNodes.isEmpty()) {
            return Collections.emptyList();
        }

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

        while (selectedNodes.size() < targetSize) {
            if (!iterator.hasNext()) {
                iterator = ring.values().iterator();
            }
            if (!iterator.hasNext()) {
                break;
            }
            StorageNode candidate = iterator.next();
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
