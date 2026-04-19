package dev.knalis.trajectaapi.event.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserUnbannedEvent {
    private long userId;
}
