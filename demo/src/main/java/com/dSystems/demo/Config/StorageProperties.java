package com.dSystems.demo.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class StorageProperties {

    private String tempDir = "temp/";
    private String uploadDir = "uploads/";
    private int maxChunksPerFile = 3;

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
}
