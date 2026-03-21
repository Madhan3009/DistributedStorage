package com.dSystems.demo.Model;

import lombok.Setter;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class FileChunkMetadata {

    @Setter
    private String fileId;
    @Setter
    private String originalFileName;
    @Setter
    private int totalChunks;
    @Setter
    private Path tempDirectory;
    private final Map<Integer, Path> chunkPaths = new TreeMap<>();

    public String getFileId() {
        return fileId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    public Map<Integer, Path> getChunkPaths() {
        return chunkPaths;
    }
}
