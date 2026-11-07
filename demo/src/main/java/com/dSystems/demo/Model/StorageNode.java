package com.dSystems.demo.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity for a storage node in the coordinator's registry: network address, health status,
 * and most recently reported disk usage.
 */
@Entity
@Table(name = "storage_nodes")
public class StorageNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique node identifier, e.g. node-1.
    @Column(nullable = false, unique = true)
    private String nodeId;

    // Hostname or IP address of the node.
    @Column(nullable = false)
    private String host;

    // Port on which the node accepts requests.
    @Column(nullable = false)
    private int port;

    // Health status:
    // - ALIVE: heartbeats are current; eligible for reads and new placements.
    // - SUSPECTED: a heartbeat has been missed; excluded from reads and new placements.
    // - DEAD: failed; replicas held by this node are re-created elsewhere.
    @Column(nullable = false)
    private String status;

    // Time of the most recent registration or heartbeat.
    @Column(nullable = false)
    private LocalDateTime lastHeartbeatAt;

    // Disk space in use, in bytes, as of the last heartbeat.
    private long diskUsedBytes;
    
    // Free disk space, in bytes, as of the last heartbeat.
    private long diskFreeBytes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public long getDiskUsedBytes() {
        return diskUsedBytes;
    }

    public void setDiskUsedBytes(long diskUsedBytes) {
        this.diskUsedBytes = diskUsedBytes;
    }

    public long getDiskFreeBytes() {
        return diskFreeBytes;
    }

    public void setDiskFreeBytes(long diskFreeBytes) {
        this.diskFreeBytes = diskFreeBytes;
    }

    // Number of missed heartbeat checks since the last successful heartbeat.
    @Column(nullable = false)
    private int missedHeartbeats = 0;

    public int getMissedHeartbeats() {
        return missedHeartbeats;
    }

    public void setMissedHeartbeats(int missedHeartbeats) {
        this.missedHeartbeats = missedHeartbeats;
    }
}
