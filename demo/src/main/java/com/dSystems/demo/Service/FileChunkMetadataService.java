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

@Service
public class FileChunkMetadataService {

    public Path metadataPath(Path chunkDirectory) {
        return chunkDirectory.resolve("metadata.properties");
    }

    public void updateMetadata(Path chunkDirectory,
                               String fileId,
                               String originalFileName,
                               int totalChunks,
                               int chunkNumber,
                               Path chunkFile) throws IOException {
        Path metadataFile = metadataPath(chunkDirectory);
        Properties properties = new Properties();

        if (Files.exists(metadataFile)) {
            try (InputStream inputStream = Files.newInputStream(metadataFile)) {
                properties.load(inputStream);
            }
        }

        properties.setProperty("fileId", fileId);
        properties.setProperty("originalFileName", originalFileName);
        properties.setProperty("totalChunks", String.valueOf(totalChunks));
        properties.setProperty("tempDirectory", chunkDirectory.toString());
        properties.setProperty("chunk." + chunkNumber, chunkFile.toString());

        try (OutputStream outputStream = Files.newOutputStream(metadataFile)) {
            properties.store(outputStream, "Chunk upload metadata");
        }
    }

    public FileChunkMetadata readMetadata(Path metadataFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(metadataFile)) {
            properties.load(inputStream);
        }

        FileChunkMetadata metadata = new FileChunkMetadata();
        metadata.setFileId(properties.getProperty("fileId"));
        metadata.setOriginalFileName(properties.getProperty("originalFileName"));
        metadata.setTotalChunks(Integer.parseInt(properties.getProperty("totalChunks")));
        metadata.setTempDirectory(Path.of(properties.getProperty("tempDirectory")));

        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("chunk.")) {
                int chunkNumber = Integer.parseInt(name.substring("chunk.".length()));
                metadata.getChunkPaths().put(chunkNumber, Path.of(properties.getProperty(name)));
            }
        }

        return metadata;
    }

    public Path rebuildFile(Path metadataFile, Path uploadDirectory) throws IOException {
        FileChunkMetadata metadata = readMetadata(metadataFile);
        if (metadata.getChunkPaths().size() != metadata.getTotalChunks()) {
            throw new IOException("Cannot rebuild file. Missing chunk metadata entries.");
        }

        Files.createDirectories(uploadDirectory);
        Path rebuiltFile = uploadDirectory.resolve(metadata.getOriginalFileName()).normalize();

        try (OutputStream outputStream = Files.newOutputStream(rebuiltFile)) {
            for (int chunkNumber = 0; chunkNumber < metadata.getTotalChunks(); chunkNumber++) {
                Path chunkPath = metadata.getChunkPaths().get(chunkNumber);
                if (chunkPath == null || !Files.exists(chunkPath)) {
                    throw new IOException("Missing chunk file for chunk " + chunkNumber);
                }
                Files.copy(chunkPath, outputStream);
            }
        }

        return rebuiltFile;
    }
}
