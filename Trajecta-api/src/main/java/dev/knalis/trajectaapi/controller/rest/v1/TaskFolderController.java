package dev.knalis.trajectaapi.controller.rest.v1;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.common.ApiResponse;
import dev.knalis.trajectaapi.dto.folder.FolderCreateRequest;
import dev.knalis.trajectaapi.dto.folder.FolderResponse;
import dev.knalis.trajectaapi.dto.folder.FolderUpdateRequest;
import dev.knalis.trajectaapi.model.task.TaskFolder;
import dev.knalis.trajectaapi.service.intrf.task.TaskFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/task-folders")
@RequiredArgsConstructor
@Tag(name = "Task Folders", description = "Hierarchical task folder management.")
@SecurityRequirement(name = "bearerAuth")
public class TaskFolderController {

    private final TaskFolderService taskFolderService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "Create folder", description = "Creates root or nested folder.")
    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(@Valid @RequestBody FolderCreateRequest request, Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        TaskFolder created = taskFolderService.create(currentUser.getId(), request.getName(), request.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toDto(created, List.of())));
    }

    @Operation(summary = "List direct children", description = "Returns immediate children for parentId. Use null for roots.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FolderResponse>>> listChildren(
            @RequestParam(required = false) Long parentId,
            Authentication auth
    ) {
        var currentUser = currentUserResolver.requireUser(auth);
        List<FolderResponse> response = taskFolderService.listChildren(currentUser.getId(), parentId).stream()
                .map(folder -> toDto(folder, List.of()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get full folder tree", description = "Returns all user folders as nested tree.")
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getTree(Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        List<TaskFolder> all = taskFolderService.listTree(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(buildTree(all)));
    }

    @Operation(summary = "Get folder by id")
    @GetMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> getById(@PathVariable Long folderId, Authentication auth) {
        var currentUser = currentUserResolver.requireUser(auth);
        TaskFolder folder = taskFolderService.get(currentUser.getId(), folderId);
        return ResponseEntity.ok(ApiResponse.success(toDto(folder, List.of())));
    }

    @Operation(summary = "Update folder", description = "Renames folder and/or moves it to another parent.")
    @PatchMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> update(
            @PathVariable Long folderId,
            @Valid @RequestBody FolderUpdateRequest request,
            Authentication auth
    ) {
        var currentUser = currentUserResolver.requireUser(auth);
        TaskFolder updated = taskFolderService.update(currentUser.getId(), folderId, request.getName(), request.getParentId());
        return ResponseEntity.ok(ApiResponse.success(toDto(updated, List.of())));
    }

    @Operation(summary = "Delete folder", description = "Deletes folder. By default recursive=true deletes all nested children.")
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "true") boolean recursive,
            Authentication auth
    ) {
        var currentUser = currentUserResolver.requireUser(auth);
        taskFolderService.delete(currentUser.getId(), folderId, recursive);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private List<FolderResponse> buildTree(List<TaskFolder> folders) {
        Map<Long, List<TaskFolder>> byParent = new HashMap<>();
        for (TaskFolder folder : folders) {
            byParent.computeIfAbsent(folder.getParentId(), ignored -> new ArrayList<>()).add(folder);
        }

        for (List<TaskFolder> siblings : byParent.values()) {
            siblings.sort(Comparator.comparing(TaskFolder::getName, String.CASE_INSENSITIVE_ORDER));
        }

        return mapChildren(null, byParent);
    }

    private List<FolderResponse> mapChildren(Long parentId, Map<Long, List<TaskFolder>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(folder -> toDto(folder, mapChildren(folder.getId(), byParent)))
                .toList();
    }

    private FolderResponse toDto(TaskFolder folder, List<FolderResponse> children) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentId(folder.getParentId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .children(children)
                .build();
    }
}

