package dev.knalis.trajectaapi.dto.ws.payload;

import dev.knalis.trajectaapi.dto.ws.WsPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserUnbannedPayload implements WsPayload {
    private long userId;
}
