package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.ChunkPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChunkPlacementRepository extends JpaRepository<ChunkPlacement, Long> {
    List<ChunkPlacement> findByFileId(String fileId);
    List<ChunkPlacement> findByFileIdAndChunkNumber(String fileId, int chunkNumber);
    List<ChunkPlacement> findByNodeId(String nodeId);
    Optional<ChunkPlacement> findByFileIdAndChunkNumberAndNodeId(String fileId, int chunkNumber, String nodeId);
    void deleteByFileId(String fileId);
}
