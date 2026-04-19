package dev.knalis.trajectaapi.dto.folder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Task folder response.")
public class FolderResponse {

    @Schema(example = "42")
    private Long id;

    @Schema(example = "Mission A")
    private String name;

    @Schema(example = "12")
    private Long parentId;

    private Instant createdAt;

    private Instant updatedAt;

    private List<FolderResponse> children;
}

