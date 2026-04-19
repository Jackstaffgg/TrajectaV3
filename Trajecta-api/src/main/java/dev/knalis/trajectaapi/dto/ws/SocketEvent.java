package dev.knalis.trajectaapi.dto.ws;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "WebSocket message envelope sent to user queues.")
public class SocketEvent {
    @Schema(description = "Event type discriminator.")
    private WsEventType type;

    @Schema(description = "Event payload. Concrete type depends on 'type'.")
    private Object payload;
}


