package dev.knalis.trajectaapi.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard API error payload.")
public class ApiError {
    @Schema(description = "HTTP status code.", example = "400")
    private int status;

    @Schema(description = "Machine-readable error code.", example = "BAD_REQUEST")
    private String code;

    @Schema(description = "Human-readable error message.", example = "Title is required")
    private String message;
}

