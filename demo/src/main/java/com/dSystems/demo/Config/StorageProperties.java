package com.dSystems.demo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized storage and cluster settings, bound from configuration properties prefixed with
 * {@code app}.
 */
@ConfigurationProperties(prefix = "app")
public class StorageProperties {

    // Directories
    // Working directory for staged chunks; also the chunk store on storage nodes.
    private String tempDir = "temp/";
    // Destination directory for files rebuilt on the coordinator.
    private String uploadDir = "uploads/";

    // Storage
    // Maximum number of chunks allowed per file.
    private int maxChunksPerFile = 3;
    // Target number of replicas maintained for each chunk.
    private int replicationFactor = 3;
    // Interval between re-replication runs, in milliseconds.
    private long healingIntervalMs = 20000;

    // Cluster
    // Role of this instance: COORDINATOR manages metadata and placement; NODE stores chunks.
    private String role = "COORDINATOR"; 
    // Base URL of the coordinator, used by storage nodes to register and send heartbeats.
    private String coordinatorUrl = "http://localhost:9000";
    // Unique identifier of this storage node.
    private String nodeId = "node-1";
    // Hostname or IP address the coordinator uses to reach this node.
    private String host = "localhost";
    // Port on which this node accepts requests.
    private int port = 9001;

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public int getMaxChunksPerFile() {
        return maxChunksPerFile;
    }

    public void setMaxChunksPerFile(int maxChunksPerFile) {
        this.maxChunksPerFile = maxChunksPerFile;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public long getHealingIntervalMs() {
        return healingIntervalMs;
    }

    public void setHealingIntervalMs(long healingIntervalMs) {
        this.healingIntervalMs = healingIntervalMs;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCoordinatorUrl() {
        return coordinatorUrl;
    }

    public void setCoordinatorUrl(String coordinatorUrl) {
        this.coordinatorUrl = coordinatorUrl;
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
}
