package dev.knalis.trajectaapi.dto.folder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update task folder request.")
public class FolderUpdateRequest {

    @Size(max = 120, message = "Folder name must not exceed 120 characters")
    @Schema(description = "New folder name. Leave null to keep current.", example = "Mission A / Retest")
    private String name;

    @Schema(description = "New parent folder id. Null moves folder to root.", example = "7")
    private Long parentId;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}

