package dev.knalis.trajectaapi.exception;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ErrorResponse {
    
    int status;
    String errorMessage;
    String errorFriendlyMessage;
    Instant timestamp;
    
}


