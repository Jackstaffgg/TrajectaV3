package dev.knalis.trajectaapi.controller.rest.v1.user;

import dev.knalis.trajectaapi.dto.user.AdminUserDetailsResponse;
import dev.knalis.trajectaapi.dto.user.UserPunishmentResponse;
import dev.knalis.trajectaapi.dto.user.UserResponse;
import dev.knalis.trajectaapi.dto.user.UserRoleUpdateRequest;
import dev.knalis.trajectaapi.mapper.UserMapper;
import dev.knalis.trajectaapi.mapper.UserPunishmentMapper;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import dev.knalis.trajectaapi.service.intrf.user.PunishmentService;
import dev.knalis.trajectaapi.service.intrf.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private PunishmentService punishmentService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserPunishmentMapper userPunishmentMapper;
    @Mock
    private Authentication authentication;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(userService, punishmentService, userMapper, userPunishmentMapper);
    }

    @Test
    void getAllUsers_returnsMappedUsers() {
        User user = new User();
        user.setId(1L);
        UserResponse dto = new UserResponse();
        dto.setId(1L);

        when(userService.findAll(0, 10)).thenReturn(List.of(user));
        when(userMapper.toDtoList(List.of(user))).thenReturn(List.of(dto));

        var response = controller.getAllUsers(0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void updateUserRole_returnsMappedUser() {
        UserRoleUpdateRequest request = new UserRoleUpdateRequest();
        request.setRole(Role.ADMIN);
        User user = new User();
        UserResponse dto = new UserResponse();

        when(userService.updateRole(3L, Role.ADMIN, authentication)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        var response = controller.updateUserRole(3L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).updateRole(3L, Role.ADMIN, authentication);
    }

    @Test
    void deleteUser_delegates() {
        var response = controller.deleteUser(4L, authentication);

        verify(userService).delete(4L, authentication);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserById_attachesActivePunishments() {
        User user = new User();
        user.setId(9L);
        AdminUserDetailsResponse details = new AdminUserDetailsResponse();
        UserPunishment punishment = new UserPunishment();
        UserPunishmentResponse punishmentDto = new UserPunishmentResponse();

        when(userService.findById(9L)).thenReturn(user);
        when(userMapper.toAdminDetailsDto(user)).thenReturn(details);
        when(punishmentService.getActivePunishments(9L)).thenReturn(List.of(punishment));
        when(userPunishmentMapper.toDto(punishment)).thenReturn(punishmentDto);

        var response = controller.getUserById(9L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getActivePunishments()).hasSize(1);
    }
}

