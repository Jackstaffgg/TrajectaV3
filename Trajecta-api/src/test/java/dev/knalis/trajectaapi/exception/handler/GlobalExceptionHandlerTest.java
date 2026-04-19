package dev.knalis.trajectaapi.exception.handler;

import dev.knalis.trajectaapi.exception.ApiErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleMaxUploadSizeExceededException_returnsPayloadTooLarge() {
        var response = handler.handleMaxUploadSizeExceededException();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(ApiErrorCodes.FILE_TOO_LARGE);
    }
}
