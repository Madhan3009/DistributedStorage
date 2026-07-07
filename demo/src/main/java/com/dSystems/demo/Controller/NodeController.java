package com.dSystems.demo.Controller;

import com.dSystems.demo.Model.StorageNode;
import com.dSystems.demo.Service.NodeRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/nodes")
public class NodeController {

    private final NodeRegistryService nodeRegistryService;

    public NodeController(NodeRegistryService nodeRegistryService) {
        this.nodeRegistryService = nodeRegistryService;
    }

    @PostMapping("/register")
    public ResponseEntity<StorageNode> registerNode(
            @RequestParam("nodeId") String nodeId,
            @RequestParam("host") String host,
            @RequestParam("port") int port
    ) {
        StorageNode node = nodeRegistryService.registerNode(nodeId, host, port);
        return ResponseEntity.ok(node);
    }

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

    @GetMapping
    public ResponseEntity<List<StorageNode>> getAllNodes() {
        return ResponseEntity.ok(nodeRegistryService.getAllNodes());
    }

    @PostMapping("/{nodeId}/fail")
    public ResponseEntity<String> simulateFailure(@PathVariable("nodeId") String nodeId) {
        nodeRegistryService.markNodeDead(nodeId);
        return ResponseEntity.ok("Node " + nodeId + " marked DEAD");
    }
}
