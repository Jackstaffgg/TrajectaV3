package dev.knalis.trajectaapi.dto.folder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create task folder request.")
public class FolderCreateRequest {

    @NotBlank(message = "Folder name is required")
    @Size(max = 120, message = "Folder name must not exceed 120 characters")
    @Schema(description = "Folder name.", example = "Mission A")
    private String name;

    @Schema(description = "Parent folder id. Null creates root folder.", example = "12")
    private Long parentId;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}

