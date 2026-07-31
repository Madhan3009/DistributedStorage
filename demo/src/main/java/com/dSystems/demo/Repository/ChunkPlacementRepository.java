package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.ChunkPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * THE CHUNK PLACEMENT MAP DATABASE CLERK / REPOSITORY.
 * 
 * This database assistant is in charge of reading, writing, and deleting records in the
 * placement map (where each chunk copy is physically located).
 */
public interface ChunkPlacementRepository extends JpaRepository<ChunkPlacement, Long> {
    
    /**
     * Finds all placement records for a specific file.
     * Useful for finding where all pieces of a file are scattered when a user wants to download it.
     */
    List<ChunkPlacement> findByFileId(String fileId);

    /**
     * Finds all placement copies (replicas) of a specific chunk number for a file.
     * e.g., Finds all 3 servers holding copy 0, 1, and 2 of "Chunk #4".
     */
    List<ChunkPlacement> findByFileIdAndChunkNumber(String fileId, int chunkNumber);

    /**
     * Lists all chunk placement records associated with a specific storage server.
     * Useful when a server crashes, so we can check exactly what files it was holding
     * and make replacement copies of those files elsewhere.
     */
    List<ChunkPlacement> findByNodeId(String nodeId);

    /**
     * Looks up a single exact placement record for a specific chunk on a specific server.
     */
    Optional<ChunkPlacement> findByFileIdAndChunkNumberAndNodeId(String fileId, int chunkNumber, String nodeId);

    /**
     * Deletes all placement records for a file.
     * Called when a user deletes a file, so we clean up our location maps.
     */
    void deleteByFileId(String fileId);

    /**
     * Finds all distinct file IDs that have at least one placement on a dead node.
     */
    @Query("SELECT DISTINCT cp.fileId FROM ChunkPlacement cp WHERE cp.nodeId IN :deadNodeIds")
    List<String> findFileIdsWithPlacementsOnNodes(@Param("deadNodeIds") List<String> deadNodeIds);
}
