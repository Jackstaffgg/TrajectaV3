package dev.knalis.trajectaapi.event.user;

import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.payload.UserBannedPayload;
import dev.knalis.trajectaapi.dto.ws.payload.UserUnbannedPayload;
import dev.knalis.trajectaapi.service.intrf.WsEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserBannedEventListener {

    private final WsEventDispatcher wsEventDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserBan(UserBannedEvent event) {
        UserBannedPayload payload = UserBannedPayload.builder()
                .userId(event.getUserId())
                .punishmentId(event.getPunishmentId())
                .reason(event.getReason())
                .expiredAt(event.getExpiredAt())
                .punishedById(event.getPunishedById())
                .punishedByName(event.getPunishedByName())
                .build();

        wsEventDispatcher.emitToUsers(List.of(event.getUserId()), WsEventType.USER_BANNED, payload);
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserUnbanned(UserUnbannedEvent event) {
        UserUnbannedPayload payload = new UserUnbannedPayload(event.getUserId());
        wsEventDispatcher.emitToUsers(List.of(event.getUserId()), WsEventType.USER_UNBANNED, payload);
    }
}

