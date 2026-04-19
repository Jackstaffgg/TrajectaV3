package dev.knalis.trajectaapi.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Data
@Schema(description = "Task creation payload for telemetry analysis.")
public class TaskCreateRequest {
    
    @Schema(description = "Task title visible to the user.", example = "Flight #102 telemetry")
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;
    
    @Schema(description = "BIN telemetry file to analyze.", type = "string", format = "binary")
    private MultipartFile file;

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
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName == null ? null : folderName.trim();
        if (this.folderName != null && this.folderName.isEmpty()) {
            this.folderName = null;
        }
    }
    
    public boolean hasValidFile() {
        if (file == null) {
            return false;
        }
        
        if (file.isEmpty()) {
            return false;
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return false;
        }
        
        String normalizedFilename = originalFilename.trim().toLowerCase(Locale.ROOT);
        return normalizedFilename.endsWith(".bin");
    }
}
