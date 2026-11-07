package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

/**
 * Persistence operations for {@link StorageNode}.
 */
public interface StorageNodeRepository extends JpaRepository<StorageNode, Long> {
    
    /**
     * Finds a node by its unique identifier.
     */
    Optional<StorageNode> findByNodeId(String nodeId);

    /**
     * Finds a node by its network address.
     */
    Optional<StorageNode> findByHostAndPort(String host, int port);

    /**
     * Finds all nodes with the given status, e.g. ALIVE.
     */
    List<StorageNode> findByStatus(String status);
}
