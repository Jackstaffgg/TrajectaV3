package dev.knalis.trajectaapi.event.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class UserBannedEvent {
    private long userId;
    private long punishmentId;
    private String reason;
    private Instant expiredAt;
    private long punishedById;
    private String punishedByName;
}
