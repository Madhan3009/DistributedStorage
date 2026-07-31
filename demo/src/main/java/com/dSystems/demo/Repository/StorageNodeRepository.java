package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

/**
 * THE STORAGE NODE REGISTRY DATABASE CLERK / REPOSITORY.
 * 
 * This database assistant keeps track of all active and inactive storage servers (worker nodes) in our cluster.
 */
public interface StorageNodeRepository extends JpaRepository<StorageNode, Long> {
    
    /**
     * Finds a storage server's details using its unique identifier (like "node-1").
     * 
     * @return An Optional box containing the server's record if found.
     */
    Optional<StorageNode> findByNodeId(String nodeId);

    /**
     * Finds a storage server using its network address (host/IP) and port number.
     * Useful for checking if a newly connected server is already registered.
     */
    Optional<StorageNode> findByHostAndPort(String host, int port);

    /**
     * Gets a list of all servers matching a specific status (e.g., "ALIVE" or "DEAD").
     * Used by the coordinator to list active storage nodes when deciding where to place new file parts.
     */
    List<StorageNode> findByStatus(String status);
}
