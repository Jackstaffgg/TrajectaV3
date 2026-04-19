package dev.knalis.trajectaapi.service.impl.user;

import dev.knalis.trajectaapi.dto.user.BanUserRequest;
import dev.knalis.trajectaapi.event.EventPublisher;
import dev.knalis.trajectaapi.exception.BadRequestException;
import dev.knalis.trajectaapi.exception.NotFoundException;
import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.model.user.punishment.PunishmentType;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import dev.knalis.trajectaapi.repo.PunishmentRepository;
import dev.knalis.trajectaapi.service.impl.cache.PunishmentCacheService;
import dev.knalis.trajectaapi.service.intrf.user.PunishmentService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PunishmentServiceImpl implements PunishmentService {
    
    private final UserService userService;
    private final PunishmentRepository punishmentRepository;
    private final EventPublisher eventPublisher;
    private final PunishmentCacheService punishmentCacheService;
    
    @Override
    @Transactional
    public UserPunishment ban(BanUserRequest request, Authentication auth) {
        if (request.getUserId() == null) {
            throw new BadRequestException("User id is required");
        }
        
        User currentUser = userService.findByUsername(auth.getName());
        User targetUser = userService.findById(request.getUserId());
        
        ensureCanPunish(currentUser, targetUser);
        
        if (request.getExpiredAt() != null && !request.getExpiredAt().isAfter(Instant.now())) {
            throw new BadRequestException("Expiration time must be in the future");
        }
        
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Reason is required");
        }
        
        if (punishmentRepository.existsActivePunishment(targetUser, PunishmentType.BAN, Instant.now())) {
            throw new BadRequestException("User is already banned");
        }
        
        UserPunishment punishment = new UserPunishment();
        punishment.setUser(targetUser);
        punishment.setPunishedBy(currentUser);
        punishment.setType(PunishmentType.BAN);
        punishment.setReason(reason);
        punishment.setExpiredAt(request.getExpiredAt());
        
        UserPunishment saved = punishmentRepository.save(punishment);

        eventPublisher.publishUserBanned(
                targetUser.getId(),
                saved.getId(),
                saved.getReason(),
                saved.getExpiredAt(),
                currentUser.getId(),
                currentUser.getName()
        );

        evictBanStateCache(targetUser.getId());
        return saved;
    }
    
    @Override
    @Transactional
    public void unban(long punishmentId, Authentication auth) {
        User currentUser = userService.findByUsername(auth.getName());
        
        UserPunishment punishment = punishmentRepository.findById(punishmentId)
                .orElseThrow(() -> new NotFoundException("Punishment not found with id: " + punishmentId));
        
        if (punishment.getType() != PunishmentType.BAN) {
            throw new BadRequestException("Punishment is not a ban");
        }
        
        ensureCanPunish(currentUser, punishment.getUser());
        
        deactivateBan(punishment);
    }

    @Override
    @Transactional
    public void unbanByUserId(long userId, Authentication auth) {
        User currentUser = userService.findByUsername(auth.getName());
        User targetUser = userService.findById(userId);

        ensureCanPunish(currentUser, targetUser);

        UserPunishment activeBan = punishmentRepository.findLatestActivePunishment(targetUser, PunishmentType.BAN, Instant.now())
                .orElseThrow(() -> new BadRequestException("User has no active ban"));

        deactivateBan(activeBan);
    }
    
    @Override
    public List<UserPunishment> getActivePunishments(long userId) {
        User user = userService.findById(userId);
        return punishmentRepository.findActivePunishments(user, Instant.now());
    }

    @Override
    public List<UserPunishment> getPunishmentsHistory(long userId) {
        User user = userService.findById(userId);
        return punishmentRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public Optional<UserPunishment> getActiveBan(long userId) {
        User user = userService.findById(userId);
        return punishmentRepository.findLatestActivePunishment(user, PunishmentType.BAN, Instant.now());
    }
    
    @Override
    public boolean isUserBanned(long userId) {
        return punishmentCacheService.isUserBanned(userId);
    }

    private void deactivateBan(UserPunishment punishment) {
        if (punishment.isExpired()) {
            throw new BadRequestException("Ban is already inactive");
        }
        
        eventPublisher.publishUserUnbanned(
                punishment.getUser().getId()
        );

        punishment.setExpiredAt(Instant.now());
        punishmentRepository.save(punishment);
        evictBanStateCache(punishment.getUser().getId());
    }
    
    private void ensureCanPunish(User actor, User target) {
        if (actor.getId().equals(target.getId())) {
            throw new PermissionDeniedException("Users cannot punish themselves");
        }
        
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.OWNER) {
            throw new PermissionDeniedException("No rights to punish users");
        }
        
        if (target.getRole() == Role.OWNER) {
            throw new PermissionDeniedException("Cannot punish owner");
        }
        
        if (actor.getRole() == Role.ADMIN && target.getRole() != Role.USER) {
            throw new PermissionDeniedException("Admin can punish only users");
        }
        
        if (actor.getRole() == Role.OWNER && target.getRole() == Role.OWNER) {
            throw new PermissionDeniedException("Cannot punish another owner");
        }
    }

    private void evictBanStateCache(Long userId) {
        punishmentCacheService.evictBanFlag(userId);
    }
}
