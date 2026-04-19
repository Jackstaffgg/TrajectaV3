package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Re-analysis request for cloning an existing task with optional parameter overrides.")
public class TaskReanalyzeRequest {

    @Schema(description = "Optional new title for the cloned task.", example = "Flight #102 telemetry (retuned)")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Schema(description = "Optional logical folder for grouping tasks.", example = "Mission A")
    @Size(max = 120, message = "Folder name must not exceed 120 characters")
    private String folderName;

    @Schema(description = "Optional folder id for task placement.", example = "7")
    @Positive(message = "Folder id must be positive")
    private Long folderId;

    @Schema(description = "Optional timeline step override used for analysis output formatting.", example = "0.1")
    @Positive(message = "Timeline step must be positive")
    private Double timelineDtSeconds;

    public void setTitle(String title) {
        this.title = title == null ? null : title.trim();
        if (this.title != null && this.title.isEmpty()) {
            this.title = null;
        }
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName == null ? null : folderName.trim();
        if (this.folderName != null && this.folderName.isEmpty()) {
            this.folderName = null;
        }
    }
}

