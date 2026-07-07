package com.dSystems.demo.Repository;

import com.dSystems.demo.Model.FileIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface FileIndexRepository extends JpaRepository<FileIndex, Long> {
    Optional<FileIndex> findByFileId(String fileId);
    List<FileIndex> findByOwnerUsername(String ownerUsername);
    boolean existsByFileId(String fileId);
}
