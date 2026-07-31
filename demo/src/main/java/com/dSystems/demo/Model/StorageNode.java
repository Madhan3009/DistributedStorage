package com.dSystems.demo.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * THE STORAGE NODE (WORKER SERVER) DATA MODEL.
 * 
 * Think of a "Storage Node" as an individual computer/server in our network that acts as a digital drawer.
 * This class is the registration card the Coordinator uses to keep track of each server's network details,
 * health status (alive or dead), and how much empty space it has.
 * 
 * Annotations:
 * - @Entity: Tells the database that this represents a table.
 * - @Table(name = "storage_nodes"): Specifies the database table name where worker server details are saved.
 */
@Entity
@Table(name = "storage_nodes")
public class StorageNode {

    // Unique internal registration number in our database.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The unique name/ID given to this storage node (e.g. "node-1").
    @Column(nullable = false, unique = true)
    private String nodeId;

    // The IP address or web address of this storage node (e.g. "192.168.1.10").
    @Column(nullable = false)
    private String host;

    // The port number (network door) this node listens to (e.g. 9001).
    @Column(nullable = false)
    private int port;

    // The health status of the node:
    // - "ALIVE": The server is up, running, and accepting files.
    // - "DEAD": The server crashed or lost connection.
    @Column(nullable = false)
    private String status; // ALIVE, DEAD

    // The exact date and time this server last sent a "heartbeat" message to prove it is still running.
    @Column(nullable = false)
    private LocalDateTime lastHeartbeatAt;

    // How much disk space (in bytes) is currently occupied on this server.
    private long diskUsedBytes;
    
    // How much free disk space (in bytes) is still available on this server.
    private long diskFreeBytes;

    // --- GETTERS AND SETTERS ---
    // Controls for reading and updating the server details.

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

    // How many consecutive heartbeat windows this node has missed.
    @Column(nullable = false)
    private int missedHeartbeats = 0;

    public int getMissedHeartbeats() {
        return missedHeartbeats;
    }

    public void setMissedHeartbeats(int missedHeartbeats) {
        this.missedHeartbeats = missedHeartbeats;
    }
}
