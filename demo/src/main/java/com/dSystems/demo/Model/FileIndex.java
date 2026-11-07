package com.dSystems.demo.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity holding file-level metadata: name, size, chunk count, owner, and status. Chunk
 * locations are tracked separately in {@link ChunkPlacement}.
 */
@Entity
@Table(name = "file_indices")
public class FileIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique file identifier used by the API and in chunk placements.
    @Column(nullable = false, unique = true)
    private String fileId;

    // Original client-supplied file name.
    @Column(nullable = false)
    private String fileName;

    // Total number of chunks in the file.
    @Column(nullable = false)
    private int totalChunks;

    // Username of the file's owner.
    @Column(nullable = false)
    private String ownerUsername;

    // Time the file record was created.
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // Lifecycle status:
    // - PENDING: upload in progress.
    // - COMPLETE: at least one replica of every chunk has been stored.
    // - CORRUPT: all replicas of at least one chunk have been lost.
    @Column(nullable = false)
    private String status;

    // Total file size in bytes; 0 until the upload completes.
    @Column(nullable = false)
    private long fileSize;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
