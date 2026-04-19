package dev.knalis.trajectaapi.service.intrf.user;

import dev.knalis.trajectaapi.dto.user.BanUserRequest;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

public interface PunishmentService {
    
    UserPunishment ban(BanUserRequest request, Authentication auth);
    
    void unban(long punishmentId, Authentication auth);

    void unbanByUserId(long userId, Authentication auth);

    Optional<UserPunishment> getActiveBan(long userId);
    
    List<UserPunishment> getActivePunishments(long userId);

    List<UserPunishment> getPunishmentsHistory(long userId);
    
    boolean isUserBanned(long userId);
}