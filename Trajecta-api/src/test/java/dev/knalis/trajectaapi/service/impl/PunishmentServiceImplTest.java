package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.dto.user.BanUserRequest;
import dev.knalis.trajectaapi.event.EventPublisher;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.model.user.punishment.PunishmentType;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import dev.knalis.trajectaapi.repo.PunishmentRepository;
import dev.knalis.trajectaapi.service.impl.cache.PunishmentCacheService;
import dev.knalis.trajectaapi.service.impl.user.PunishmentServiceImpl;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private PunishmentRepository punishmentRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private PunishmentCacheService punishmentCacheService;
    @Mock
    private Authentication authentication;

    private PunishmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PunishmentServiceImpl(userService, punishmentRepository, eventPublisher, punishmentCacheService);
    }

    @Test
    void ban_evictsBanFlagCacheForTargetUser() {
        User admin = new User();
        admin.setId(1L);
        admin.setName("Admin");
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);

        User target = new User();
        target.setId(2L);
        target.setName("User");
        target.setUsername("user");
        target.setRole(Role.USER);

        BanUserRequest request = new BanUserRequest();
        request.setUserId(2L);
        request.setReason("Violation");
        request.setExpiredAt(Instant.now().plusSeconds(3600));

        when(authentication.getName()).thenReturn("admin");
        when(userService.findByUsername("admin")).thenReturn(admin);
        when(userService.findById(2L)).thenReturn(target);
        when(punishmentRepository.existsActivePunishment(any(), any(), any())).thenReturn(false);
        when(punishmentRepository.save(any(UserPunishment.class))).thenAnswer(invocation -> {
            UserPunishment punishment = invocation.getArgument(0);
            punishment.setId(100L);
            return punishment;
        });

        service.ban(request, authentication);

        verify(punishmentCacheService).evictBanFlag(2L);
    }

    @Test
    void unban_evictsBanFlagCacheForTargetUser() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);

        User target = new User();
        target.setId(2L);
        target.setRole(Role.USER);

        UserPunishment punishment = new UserPunishment();
        punishment.setId(101L);
        punishment.setUser(target);
        punishment.setPunishedBy(admin);
        punishment.setType(PunishmentType.BAN);
        punishment.setReason("Violation");
        punishment.setExpiredAt(null);

        when(authentication.getName()).thenReturn("admin");
        when(userService.findByUsername("admin")).thenReturn(admin);
        when(punishmentRepository.findById(101L)).thenReturn(Optional.of(punishment));

        service.unban(101L, authentication);

        verify(punishmentCacheService).evictBanFlag(2L);
    }

    @Test
    void unbanByUserId_evictsBanFlagCacheForTargetUser() {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(Role.OWNER);

        User target = new User();
        target.setId(3L);
        target.setRole(Role.ADMIN);

        UserPunishment activeBan = new UserPunishment();
        activeBan.setId(202L);
        activeBan.setUser(target);
        activeBan.setPunishedBy(owner);
        activeBan.setType(PunishmentType.BAN);
        activeBan.setReason("Abuse");

        when(authentication.getName()).thenReturn("owner");
        when(userService.findByUsername("owner")).thenReturn(owner);
        when(userService.findById(3L)).thenReturn(target);
        when(punishmentRepository.findLatestActivePunishment(any(), any(), any())).thenReturn(Optional.of(activeBan));

        service.unbanByUserId(3L, authentication);

        verify(punishmentCacheService).evictBanFlag(3L);
    }

    @Test
    void isUserBanned_delegatesToPunishmentCacheService() {
        when(punishmentCacheService.isUserBanned(9L)).thenReturn(true);

        boolean result = service.isUserBanned(9L);

        org.assertj.core.api.Assertions.assertThat(result).isTrue();
        verify(punishmentCacheService).isUserBanned(9L);
        verify(punishmentRepository, never()).existsActivePunishment(any(), any(), any());
    }
}
