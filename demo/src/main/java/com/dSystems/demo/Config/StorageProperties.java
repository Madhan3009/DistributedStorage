package com.dSystems.demo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class StorageProperties {

    private String tempDir = "temp/";
    private String uploadDir = "uploads/";
    private int maxChunksPerFile = 3;
    private int replicationFactor = 3;
    private String role = "COORDINATOR"; // COORDINATOR, NODE
    private String coordinatorUrl = "http://localhost:9000";
    private String nodeId = "node-1";
    private String host = "localhost";
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
