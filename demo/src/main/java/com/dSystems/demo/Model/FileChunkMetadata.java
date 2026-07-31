package com.dSystems.demo.Model;

import lombok.Setter;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * IN-MEMORY FILE UPLOAD HELPER (METADATA).
 * 
 * Think of this class as a temporary "scratchpad" or "receipt" used while a file is in the process
 * of being uploaded, split into pieces, or put back together.
 * Notice that it doesn't have database annotations (@Entity) because we don't save this to a database;
 * it only lives in the server's temporary memory (RAM) while active work is being done.
 */
public class FileChunkMetadata {

    // Unique system ID (barcode) of the file being processed.
    // - @Setter: A Lombok library helper that automatically creates "set" methods (like setFileId)
    //   behind the scenes so we don't have to type them.
    @Setter
    private String fileId;
    
    // The original name of the file (e.g. "resume.docx").
    @Setter
    private String originalFileName;
    
    // The number of pieces the file will be split into.
    @Setter
    private int totalChunks;
    
    // The folder path where the file parts are temporarily held on the server's drive.
    @Setter
    private Path tempDirectory;
    
    // A sorted dictionary/list mapping each piece's number (e.g. 0, 1, 2) to its temporary file location on the disk.
    // We use a TreeMap to ensure the parts stay in order (0, 1, 2...) so we can reassemble them correctly.
    private final Map<Integer, Path> chunkPaths = new TreeMap<>();

    // --- GETTERS ---
    // Methods to retrieve the metadata values.

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
