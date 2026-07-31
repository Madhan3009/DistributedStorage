package com.dSystems.demo.Service;

import com.dSystems.demo.Model.FileChunkMetadata;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * THE LOCAL STORAGE METADATA CLERK.
 * 
 * Think of this class as a helper that writes and reads simple "index cards" (saved as text files
 * named `metadata.properties` on the computer's hard drive) to keep track of file parts stored locally.
 * 
 * Instead of talking to a central SQL database, it reads and writes simple key-value text files
 * directly on the disk (e.g. `fileId=abc`, `chunk.0=/temp/chunk-0.part`).
 */
@Service
public class FileChunkMetadataService {

    /**
     * Helper to get the path of the "index card" (metadata.properties) file inside a directory.
     */
    public Path metadataPath(Path chunkDirectory) {
        return chunkDirectory.resolve("metadata.properties");
    }

    /**
     * Writes or updates the "index card" on the hard drive with details about a newly received file chunk.
     * 
     * @param chunkDirectory The folder where the file's chunks are saved.
     * @param fileId The unique file ID.
     * @param originalFileName The original file name.
     * @param totalChunks The total number of pieces.
     * @param chunkNumber The index number of the chunk we just received.
     * @param chunkFile The physical file path of the chunk we just saved.
     */
    public void updateMetadata(Path chunkDirectory,
                               String fileId,
                               String originalFileName,
                               int totalChunks,
                               int chunkNumber,
                               Path chunkFile) throws IOException {
        Path metadataFile = metadataPath(chunkDirectory);
        Properties properties = new Properties(); // Java helper to read/write key=value text files

        // If the metadata file already exists, load its existing content first
        if (Files.exists(metadataFile)) {
            try (InputStream inputStream = Files.newInputStream(metadataFile)) {
                properties.load(inputStream);
            }
        }

        // Add or update the details
        properties.setProperty("fileId", fileId);
        properties.setProperty("originalFileName", originalFileName);
        properties.setProperty("totalChunks", String.valueOf(totalChunks));
        properties.setProperty("tempDirectory", chunkDirectory.toString());
        properties.setProperty("chunk." + chunkNumber, chunkFile.toString()); // e.g. "chunk.1=/path/to/part"

        // Save the updated properties back to the text file on the hard drive
        try (OutputStream outputStream = Files.newOutputStream(metadataFile)) {
            properties.store(outputStream, "Chunk upload metadata");
        }
    }

    /**
     * Reads a `metadata.properties` file from disk and parses it back into a Java object.
     * 
     * @param metadataFile The path to the metadata.properties file.
     * @return FileChunkMetadata A structured Java object filled with the parsed details.
     */
    public FileChunkMetadata readMetadata(Path metadataFile) throws IOException {
        Properties properties = new Properties();
        // Load the key-value pairs from the file
        try (InputStream inputStream = Files.newInputStream(metadataFile)) {
            properties.load(inputStream);
        }

        // Create a new empty Java metadata receipt and fill it out
        FileChunkMetadata metadata = new FileChunkMetadata();
        metadata.setFileId(properties.getProperty("fileId"));
        metadata.setOriginalFileName(properties.getProperty("originalFileName"));
        metadata.setTotalChunks(Integer.parseInt(properties.getProperty("totalChunks")));
        metadata.setTempDirectory(Path.of(properties.getProperty("tempDirectory")));

        // Loop through all keys. If a key starts with "chunk.", extract the number and the file path
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("chunk.")) {
                int chunkNumber = Integer.parseInt(name.substring("chunk.".length()));
                metadata.getChunkPaths().put(chunkNumber, Path.of(properties.getProperty(name)));
            }
        }

        return metadata;
    }

    /**
     * Rebuilds/stitches a file locally on a storage node by copying its pieces together in order.
     * 
     * @param metadataFile The path to the index card file.
     * @param uploadDirectory The folder where the assembled file should be saved.
     * @return Path The path of the newly reconstructed file.
     */
    public Path rebuildFile(Path metadataFile, Path uploadDirectory) throws IOException {
        // Read the index card
        FileChunkMetadata metadata = readMetadata(metadataFile);
        
        // Ensure we actually have all the parts listed before attempting to build
        if (metadata.getChunkPaths().size() != metadata.getTotalChunks()) {
            throw new IOException("Cannot rebuild file. Missing chunk metadata entries.");
        }

        Files.createDirectories(uploadDirectory);
        Path rebuiltFile = uploadDirectory.resolve(metadata.getOriginalFileName()).normalize();

        // Open the final output file stream
        try (OutputStream outputStream = Files.newOutputStream(rebuiltFile)) {
            // Loop through the pieces in order (0, 1, 2...)
            for (int chunkNumber = 0; chunkNumber < metadata.getTotalChunks(); chunkNumber++) {
                Path chunkPath = metadata.getChunkPaths().get(chunkNumber);
                if (chunkPath == null || !Files.exists(chunkPath)) {
                    throw new IOException("Missing chunk file for chunk " + chunkNumber);
                }
                // Copy all bytes from this piece directly into the assembled file
                Files.copy(chunkPath, outputStream);
            }
        }

        return rebuiltFile;
    }
}
