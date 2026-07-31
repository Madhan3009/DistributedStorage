package com.dSystems.demo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CONFIGURATION SETTINGS / PROPERTIES.
 * 
 * Think of this class as a "settings form" or a "control panel" with knobs and switches.
 * It holds the configuration variables for our distributed storage system. 
 * Instead of writing these values directly into the code (which makes them hard to change),
 * Spring loads these values from an external file (like `application.properties`) and maps them here.
 * 
 * Annotations:
 * - @ConfigurationProperties(prefix = "app"): Tells the app to look for settings that start with "app."
 *   in the configuration file (e.g., `app.temp-dir=temp/`) and automatically assign them to the variables below.
 */
@ConfigurationProperties(prefix = "app")
public class StorageProperties {

    // --- 1. DIRECTORIES ---
    // The temporary folder path used for holding files while they are processed.
    private String tempDir = "temp/";
    // The main directory where uploaded files are stored.
    private String uploadDir = "uploads/";

    // --- 2. STORAGE SYSTEM SETTINGS ---
    // The maximum number of segments/chunks we split a single file into.
    private int maxChunksPerFile = 3;
    // The number of copies (replicas) of each chunk we save across the system to prevent data loss.
    private int replicationFactor = 3;
    // The periodic healing check interval in milliseconds
    private long healingIntervalMs = 20000;

    // --- 3. CLUSTER & ROLE SETTINGS ---
    // The role of this specific running program in the system.
    // - "COORDINATOR": The main boss server that organizes who stores what.
    // - "NODE": A worker storage server that actually stores the file chunks.
    private String role = "COORDINATOR"; 
    // The web address of the coordinator server, so worker nodes know where to report.
    private String coordinatorUrl = "http://localhost:9000";
    // A unique identifier name for this storage node.
    private String nodeId = "node-1";
    // The host name or IP address of this node.
    private String host = "localhost";
    // The port number (door number) this node uses to listen for network traffic.
    private int port = 9001;

    // --- GETTERS AND SETTERS ---
    // In Java, we make properties "private" (hidden) for safety, and use "getters" (to read)
    // and "setters" (to change) as controlled windows to access these values.

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
