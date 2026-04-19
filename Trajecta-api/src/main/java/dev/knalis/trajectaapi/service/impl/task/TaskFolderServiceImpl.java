package dev.knalis.trajectaapi.service.impl.task;

import dev.knalis.trajectaapi.exception.BadRequestException;
import dev.knalis.trajectaapi.exception.NotFoundException;
import dev.knalis.trajectaapi.model.task.TaskFolder;
import dev.knalis.trajectaapi.repo.TaskFolderRepository;
import dev.knalis.trajectaapi.service.intrf.task.TaskFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskFolderServiceImpl implements TaskFolderService {

    private final TaskFolderRepository taskFolderRepository;

    @Override
    @Transactional
    public TaskFolder create(Long userId, String name, Long parentId) {
        String normalizedName = normalizeName(name);
        Long normalizedParentId = normalizeParentId(parentId);

        if (normalizedParentId != null) {
            ensureExistsForUser(normalizedParentId, userId);
        }

        ensureUniqueSiblingName(userId, normalizedParentId, normalizedName, null);

        TaskFolder folder = new TaskFolder();
        folder.setUserId(userId);
        folder.setName(normalizedName);
        folder.setParentId(normalizedParentId);
        return taskFolderRepository.save(folder);
    }

    @Override
    public TaskFolder get(Long userId, Long folderId) {
        return ensureExistsForUser(folderId, userId);
    }

    @Override
    public List<TaskFolder> listChildren(Long userId, Long parentId) {
        Long normalizedParentId = normalizeParentId(parentId);
        if (normalizedParentId != null) {
            ensureExistsForUser(normalizedParentId, userId);
        }
        return taskFolderRepository.findByUserIdAndParentIdOrderByNameAsc(userId, normalizedParentId);
    }

    @Override
    public List<TaskFolder> listTree(Long userId) {
        return taskFolderRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Override
    @Transactional
    public TaskFolder update(Long userId, Long folderId, String name, Long parentId) {
        TaskFolder folder = ensureExistsForUser(folderId, userId);

        String normalizedName = name == null ? folder.getName() : normalizeName(name);
        Long normalizedParentId = parentId == null ? null : normalizeParentId(parentId);

        if (Objects.equals(normalizedParentId, folderId)) {
            throw new BadRequestException("Folder cannot be parent of itself");
        }

        if (normalizedParentId != null) {
            ensureExistsForUser(normalizedParentId, userId);
            ensureNoCycle(folderId, normalizedParentId, userId);
        }

        ensureUniqueSiblingName(userId, normalizedParentId, normalizedName, folderId);

        folder.setName(normalizedName);
        folder.setParentId(normalizedParentId);
        return taskFolderRepository.save(folder);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long folderId, boolean recursive) {
        TaskFolder folder = ensureExistsForUser(folderId, userId);

        List<TaskFolder> children = taskFolderRepository.findByUserIdAndParentId(userId, folder.getId());
        if (!recursive && !children.isEmpty()) {
            throw new BadRequestException("Folder has child folders. Use recursive delete.");
        }

        if (recursive) {
            for (TaskFolder child : children) {
                delete(userId, child.getId(), true);
            }
        }

        taskFolderRepository.delete(folder);
    }

    private TaskFolder ensureExistsForUser(Long folderId, Long userId) {
        if (folderId == null || folderId <= 0) {
            throw new BadRequestException("Folder id must be positive");
        }
        return taskFolderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new NotFoundException("Folder not found"));
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Folder name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new BadRequestException("Folder name must not exceed 120 characters");
        }
        return normalized;
    }

    private Long normalizeParentId(Long parentId) {
        if (parentId == null) {
            return null;
        }
        if (parentId <= 0) {
            throw new BadRequestException("Parent folder id must be positive");
        }
        return parentId;
    }

    private void ensureNoCycle(Long folderId, Long newParentId, Long userId) {
        Long cursor = newParentId;
        Set<Long> guard = new HashSet<>();
        while (cursor != null) {
            if (!guard.add(cursor)) {
                throw new BadRequestException("Detected folder cycle");
            }
            if (Objects.equals(cursor, folderId)) {
                throw new BadRequestException("Folder move would create a cycle");
            }
            TaskFolder parent = ensureExistsForUser(cursor, userId);
            cursor = parent.getParentId();
        }
    }

    private void ensureUniqueSiblingName(Long userId, Long parentId, String name, Long selfId) {
        List<TaskFolder> siblings = taskFolderRepository.findByUserIdAndParentIdOrderByNameAsc(userId, parentId);
        for (TaskFolder sibling : siblings) {
            if (selfId != null && Objects.equals(sibling.getId(), selfId)) {
                continue;
            }
            if (sibling.getName() != null && sibling.getName().equalsIgnoreCase(name)) {
                throw new BadRequestException("Folder with this name already exists in the same parent");
            }
        }
    }
}

