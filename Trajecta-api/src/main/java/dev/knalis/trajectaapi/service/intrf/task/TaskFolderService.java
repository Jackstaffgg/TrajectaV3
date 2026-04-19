package dev.knalis.trajectaapi.service.intrf.task;

import dev.knalis.trajectaapi.model.task.TaskFolder;

import java.util.List;

public interface TaskFolderService {
    TaskFolder create(Long userId, String name, Long parentId);

    TaskFolder get(Long userId, Long folderId);

    List<TaskFolder> listChildren(Long userId, Long parentId);

    List<TaskFolder> listTree(Long userId);

    TaskFolder update(Long userId, Long folderId, String name, Long parentId);

    void delete(Long userId, Long folderId, boolean recursive);
}

