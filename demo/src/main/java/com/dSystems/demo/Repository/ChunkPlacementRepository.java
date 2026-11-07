package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.ChunkPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link ChunkPlacement}.
 */
public interface ChunkPlacementRepository extends JpaRepository<ChunkPlacement, Long> {
    
    /**
     * Finds the placements of all chunks of a file.
     */
    List<ChunkPlacement> findByFileId(String fileId);

    /**
     * Finds the placements of every replica of one chunk.
     */
    List<ChunkPlacement> findByFileIdAndChunkNumber(String fileId, int chunkNumber);

    /**
     * Finds all placements held by a storage node.
     */
    List<ChunkPlacement> findByNodeId(String nodeId);

    /**
     * Finds the placement of a chunk on a specific node, if any.
     */
    Optional<ChunkPlacement> findByFileIdAndChunkNumberAndNodeId(String fileId, int chunkNumber, String nodeId);

    /**
     * Deletes all placements for a file.
     */
    void deleteByFileId(String fileId);

    /**
     * Finds the distinct IDs of files with at least one placement on the given nodes.
     */
    @Query("SELECT DISTINCT cp.fileId FROM ChunkPlacement cp WHERE cp.nodeId IN :deadNodeIds")
    List<String> findFileIdsWithPlacementsOnNodes(@Param("deadNodeIds") List<String> deadNodeIds);
}
