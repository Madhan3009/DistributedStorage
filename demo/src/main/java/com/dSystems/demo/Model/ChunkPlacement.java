package com.dSystems.demo.Model;

import jakarta.persistence.*;

/**
 * THE CHUNK LOCATION / PLACEMENT MAP.
 * 
 * Since files are sliced into multiple pieces (chunks) and duplicated across different servers,
 * we need a map to know where every single piece is stored.
 * This class represents a single record in that map, answering the question:
 * "Where is piece X of file Y stored, and is it a primary copy or a backup?"
 * 
 * Annotations:
 * - @Entity: Represents a table in our database.
 * - @Table(name = "chunk_placements"): The database table is named "chunk_placements".
 */
@Entity
@Table(name = "chunk_placements")
public class ChunkPlacement {

    // Unique map record ID.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The unique ID (barcode) of the file this piece belongs to.
    @Column(nullable = false)
    private String fileId;

    // The sequence index of the chunk (e.g., Part 0, Part 1, Part 2).
    @Column(nullable = false)
    private int chunkNumber;

    // The name of the storage node (worker server) where this chunk is stored.
    @Column(nullable = false)
    private String nodeId;

    // The copy index number (e.g., 0 is the main/primary copy, 1 is the first backup, 2 is the second backup).
    @Column(nullable = false)
    private int replicaIndex; // 0, 1, 2...

    // --- GETTERS AND SETTERS ---
    // Methods to access and update chunk placement information.

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

    public int getChunkNumber() {
        return chunkNumber;
    }

    public void setChunkNumber(int chunkNumber) {
        this.chunkNumber = chunkNumber;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getReplicaIndex() {
        return replicaIndex;
    }

    public void setReplicaIndex(int replicaIndex) {
        this.replicaIndex = replicaIndex;
    }
}
