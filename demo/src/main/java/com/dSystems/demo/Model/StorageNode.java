package com.dSystems.demo.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "storage_nodes")
public class StorageNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nodeId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String status; // ALIVE, DEAD

    @Column(nullable = false)
    private LocalDateTime lastHeartbeatAt;

    private long diskUsedBytes;
    private long diskFreeBytes;

    // Getters and Setters

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
}
