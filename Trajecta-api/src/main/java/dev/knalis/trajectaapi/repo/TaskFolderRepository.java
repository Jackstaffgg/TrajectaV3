package dev.knalis.trajectaapi.repo;

import dev.knalis.trajectaapi.model.task.TaskFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskFolderRepository extends JpaRepository<TaskFolder, Long> {
    Optional<TaskFolder> findByIdAndUserId(Long id, Long userId);

    List<TaskFolder> findByUserIdOrderByNameAsc(Long userId);

    List<TaskFolder> findByUserIdAndParentIdOrderByNameAsc(Long userId, Long parentId);

    List<TaskFolder> findByUserIdAndParentId(Long userId, Long parentId);

    boolean existsByUserIdAndParentIdAndNameIgnoreCase(Long userId, Long parentId, String name);
}

