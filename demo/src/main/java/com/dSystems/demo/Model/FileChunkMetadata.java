package com.dSystems.demo.Model;

import lombok.Setter;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * In-memory representation of the metadata for a locally staged file, as read from its
 * {@code metadata.properties} file. Not a persistent entity.
 */
public class FileChunkMetadata {

    // Unique file identifier.
    @Setter
    private String fileId;
    
    // Original client-supplied file name.
    @Setter
    private String originalFileName;
    
    // Total number of chunks in the file.
    @Setter
    private int totalChunks;
    
    // Directory containing the staged chunk files.
    @Setter
    private Path tempDirectory;
    
    // Chunk paths keyed by chunk number. Sorted so that chunks iterate in file order.
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
