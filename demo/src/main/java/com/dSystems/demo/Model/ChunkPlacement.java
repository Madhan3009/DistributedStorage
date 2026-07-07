package com.dSystems.demo.Model;

import jakarta.persistence.*;

@Entity
@Table(name = "chunk_placements")
public class ChunkPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileId;

    @Column(nullable = false)
    private int chunkNumber;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private int replicaIndex; // 0, 1, 2...

    // Getters and Setters

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
