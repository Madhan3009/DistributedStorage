package com.dSystems.demo.Controller;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.NodeRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cluster membership API on the coordinator. Storage nodes register and send heartbeats using the
 * cluster token; node listing and failure simulation require a user JWT.
 */
@RestController
@RequestMapping("/nodes")
public class NodeController {

    private final NodeRegistryService nodeRegistryService;

    public NodeController(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    /**
     * Registers a storage node, or updates the address of a known node.
     * {@code POST /nodes/register}
     *
     * @param nodeId unique node identifier
     * @param host hostname or IP address of the node
     * @param port port on which the node accepts requests
     */
    @PostMapping("/register")
    public ResponseEntity<StorageNode> registerNode(
            @RequestParam("nodeId") String nodeId,
            @RequestParam("host") String host,
            @RequestParam("port") int port
    ) {
        StorageNode node = nodeRegistryService.registerNode(nodeId, host, port);
        return ResponseEntity.ok(node);
    }

    /**
     * Records a heartbeat from a registered node.
     * {@code POST /nodes/heartbeat}
     *
     * @param nodeId unique node identifier
     * @param diskUsed disk space in use on the node, in bytes
     * @param diskFree free disk space on the node, in bytes
     * @return 200 if processed, or 404 if the node is not registered
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<String> heartbeat(
            @RequestParam("nodeId") String nodeId,
            @RequestParam("diskUsed") long diskUsed,
            @RequestParam("diskFree") long diskFree
    ) {
        boolean success = nodeRegistryService.processHeartbeat(nodeId, diskUsed, diskFree);
        if (success) {
            return ResponseEntity.ok("Heartbeat processed");
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lists all registered storage nodes.
     * {@code GET /nodes}
     */
    @GetMapping
    public ResponseEntity<List<StorageNode>> getAllNodes() {
        return ResponseEntity.ok(nodeRegistryService.getAllNodes());
    }

    /**
     * Marks a node DEAD to simulate a failure.
     * {@code POST /nodes/{nodeId}/fail}
     */
    @PostMapping("/{nodeId}/fail")
    public ResponseEntity<String> simulateFailure(@PathVariable("nodeId") String nodeId) {
        nodeRegistryService.markNodeDead(nodeId);
        return ResponseEntity.ok("Node " + nodeId + " marked DEAD");
    }
}
