package com.dSystems.demo.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * THE FILE DIRECTORY / CATALOG INDEX.
 * 
 * When a user uploads a file, we don't store it as one big file on a single computer.
 * Instead, we chop it up into smaller parts (chunks) and scatter them across different servers.
 * This class acts like a "card catalog" in a library, keeping track of the overall file details:
 * its name, its size, how many pieces it was split into, and who owns it.
 * 
 * Annotations:
 * - @Entity: Tells the database this class represents a table.
 * - @Table(name = "file_indices"): The database table is named "file_indices".
 */
@Entity
@Table(name = "file_indices")
public class FileIndex {

    // Unique catalog record identifier.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A unique system-generated key (like a barcode) to reference the file.
    @Column(nullable = false, unique = true)
    private String fileId;

    // The user-facing name of the file (e.g. "my_presentation.pdf").
    @Column(nullable = false)
    private String fileName;

    // How many pieces (chunks) this file has been split into.
    @Column(nullable = false)
    private int totalChunks;

    // The username of the account that uploaded and owns the file.
    @Column(nullable = false)
    private String ownerUsername;

    // The exact date and time the file was uploaded.
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // The status of the file in our storage system:
    // - "PENDING": Currently being uploaded or split.
    // - "COMPLETE": All pieces are successfully stored across the servers.
    // - "DEGRADED": Some server copies were lost, but we still have enough copies to recover the file.
    @Column(nullable = false)
    private String status; // e.g., PENDING, COMPLETE, DEGRADED

    // The total file size in bytes (e.g., 1,048,576 bytes = 1 Megabyte).
    @Column(nullable = false)
    private long fileSize;

    // --- GETTERS AND SETTERS ---
    // Methods to access and update file index information safely.

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
