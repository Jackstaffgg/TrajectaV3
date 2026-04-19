package dev.knalis.trajectaapi.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic API response envelope.")
public class ApiResponse<T> {
    @Schema(description = "Indicates successful operation.", example = "true")
    private boolean success;

    @Schema(description = "Operation result payload. Present when success=true.")
    private T data;

    @Schema(description = "Error payload. Present when success=false.")
    private ApiError error;

    @Schema(description = "Response generation timestamp in UTC.", example = "2026-04-03T18:24:50Z")
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .error(null)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiResponse<Void> failure(int status, String code, String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .data(null)
                .error(ApiError.builder().status(status).code(code).message(message).build())
                .timestamp(Instant.now())
                .build();
    }
}

