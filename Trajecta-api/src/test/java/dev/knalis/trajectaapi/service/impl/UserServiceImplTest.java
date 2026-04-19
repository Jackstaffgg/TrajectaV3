package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.dto.auth.RegisterRequest;
import dev.knalis.trajectaapi.dto.user.UserUpdateRequest;
import dev.knalis.trajectaapi.exception.BadRequestException;
import dev.knalis.trajectaapi.exception.FieldAlreadyExistException;
import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.mapper.UserMapper;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.repo.PunishmentRepository;
import dev.knalis.trajectaapi.repo.UserRepository;
import dev.knalis.trajectaapi.service.impl.cache.UserCacheService;
import dev.knalis.trajectaapi.service.impl.user.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PunishmentRepository punishmentRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserCacheService userCacheService;
    @Mock
    private Authentication authentication;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, punishmentRepository, passwordEncoder, userMapper, userCacheService);
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");
        request.setPassword("password123");
        request.setEmail("j@x.com");
        request.setName("John");

        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("j@x.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("ENC");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.register(request);

        assertThat(saved.getPassword()).isEqualTo("ENC");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void register_throwsWhenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john");

        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request)).isInstanceOf(FieldAlreadyExistException.class);
    }

    @Test
    void updateRole_deniesNonOwner() {
        User actor = new User();
        actor.setId(1L);
        actor.setUsername("admin");
        actor.setRole(Role.ADMIN);

        User target = new User();
        target.setId(2L);
        target.setRole(Role.USER);

        when(authentication.getName()).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateRole(2L, Role.ADMIN, authentication))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void updateRole_ownerCanPromoteUser() {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(Role.OWNER);

        User target = new User();
        target.setId(2L);
        target.setRole(Role.USER);

        when(authentication.getName()).thenReturn("owner");
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        User updated = service.updateRole(2L, Role.ADMIN, authentication);

        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void delete_deniesSelfDelete() {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(Role.OWNER);

        when(authentication.getName()).thenReturn("owner");
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.delete(10L, authentication))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void updateCurrentUser_checksUniqueUsername() {
        User current = new User();
        current.setId(1L);
        current.setUsername("old");
        current.setEmail("old@x.com");

        UserUpdateRequest req = new UserUpdateRequest();
        req.setUsername("new");

        when(authentication.getName()).thenReturn("old");
        when(userRepository.findByUsername("old")).thenReturn(Optional.of(current));
        when(userRepository.existsByUsername("new")).thenReturn(false);
        when(userRepository.save(current)).thenReturn(current);

        User saved = service.updateCurrentUser(authentication, req);

        assertThat(saved).isSameAs(current);
        verify(userMapper).updateUserFromDto(req, current);
    }

    @Test
    void updateRole_throwsWhenRoleAlreadySet() {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(Role.OWNER);

        User target = new User();
        target.setId(2L);
        target.setRole(Role.USER);

        when(authentication.getName()).thenReturn("owner");
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.updateRole(2L, Role.USER, authentication))
                .isInstanceOf(BadRequestException.class);
    }
}
