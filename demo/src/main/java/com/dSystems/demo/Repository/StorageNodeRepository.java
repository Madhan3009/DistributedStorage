package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface StorageNodeRepository extends JpaRepository<StorageNode, Long> {
    Optional<StorageNode> findByNodeId(String nodeId);
    Optional<StorageNode> findByHostAndPort(String host, int port);
    List<StorageNode> findByStatus(String status);
}
