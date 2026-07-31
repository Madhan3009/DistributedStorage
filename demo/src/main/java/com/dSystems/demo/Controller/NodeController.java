package com.dSystems.demo.Controller;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.NodeRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * THE COORDINATOR'S SERVER REGISTRY GATEWAY.
 * 
 * Think of this class as the entrance gate reception window specifically for the storage servers (worker nodes).
 * Storage servers send requests to this window to:
 * 1. Register: Introduce themselves to the coordinator when they start up.
 * 2. Heartbeat: Send a check-in report every 10 seconds containing their current disk usage stats.
 * 3. List: Let administrators get a list of all nodes in the system (e.g. for a dashboard UI).
 * 4. Fail: Let administrators simulate a node crash (for testing system self-healing).
 * 
 * Annotations:
 * - @RestController: Marks this as a JSON-producing web endpoint controller.
 * - @RequestMapping("/nodes"): Prepends "/nodes" to all web paths in this file.
 */
@RestController
@RequestMapping("/nodes")
public class NodeController {

    // Injected service that manages the active server registry database
    private final NodeRegistryService nodeRegistryService;

    /**
     * Constructor - Receives the registry service helper.
     */
    public NodeController(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    /**
     * SERVICE: Register a storage node.
     * Web URL: POST http://localhost:8080/nodes/register
     * 
     * @param nodeId Unique ID of the server (e.g., "node-1").
     * @param host IP address or hostname.
     * @param port Network port.
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
     * SERVICE: Receive a heartbeat check-in signal.
     * Web URL: POST http://localhost:8080/nodes/heartbeat
     * 
     * @param diskUsed Current bytes of disk space used on the node.
     * @param diskFree Remaining bytes of free space left on the node.
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
            // If the node tries to check in but isn't registered yet, return a 404 (Not Found)
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * SERVICE: Get a list of all registered storage servers.
     * Web URL: GET http://localhost:8080/nodes
     */
    @GetMapping
    public ResponseEntity<List<StorageNode>> getAllNodes() {
        return ResponseEntity.ok(nodeRegistryService.getAllNodes());
    }

    /**
     * SERVICE: Simulate a server crash (fail a node).
     * Web URL: POST http://localhost:8080/nodes/{nodeId}/fail
     * 
     * - @PathVariable: Extracts the nodeId directly from the web address path.
     */
    @PostMapping("/{nodeId}/fail")
    public ResponseEntity<String> simulateFailure(@PathVariable("nodeId") String nodeId) {
        // Force-mark the node as DEAD in the database
        nodeRegistryService.markNodeDead(nodeId);
        return ResponseEntity.ok("Node " + nodeId + " marked DEAD");
    }
}
