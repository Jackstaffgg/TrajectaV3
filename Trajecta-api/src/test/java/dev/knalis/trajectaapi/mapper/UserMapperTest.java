package dev.knalis.trajectaapi.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.trajectaapi.dto.user.UserUpdateRequest;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserMapperTest {

    private UserMapperImpl mapper;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mapper = new UserMapperImpl();
        passwordEncoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(mapper, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(mapper, "objectMapper", new ObjectMapper());
    }

    @Test
    void updateUserFromDto_updatesProvidedFieldsAndEncodesPassword() {
        User user = new User();
        user.setUsername("old");

        UserUpdateRequest req = new UserUpdateRequest();
        req.setUsername("new");
        req.setPassword("password123");

        when(passwordEncoder.encode("password123")).thenReturn("ENC");

        mapper.updateUserFromDto(req, user);

        assertThat(user.getUsername()).isEqualTo("new");
        assertThat(user.getPassword()).isEqualTo("ENC");
    }

    @Test
    void updateUserFromDto_handlesNullAndBlankPasswordBranches() {
        User user = new User();
        user.setPassword("OLD");

        UserUpdateRequest nullPassword = new UserUpdateRequest();
        nullPassword.setName("N");
        mapper.updateUserFromDto(nullPassword, user);
        assertThat(user.getPassword()).isEqualTo("OLD");

        UserUpdateRequest blankPassword = new UserUpdateRequest();
        blankPassword.setPassword("   ");
        mapper.updateUserFromDto(blankPassword, user);
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void mappingMethods_handleNullInputs() {
        assertThat(mapper.toDto(null)).isNull();
        assertThat(mapper.toDtoList(null)).isNull();

        User user = new User();
        user.setId(5L);
        user.setRole(Role.USER);
        assertThat(mapper.toDtoList(List.of(user))).hasSize(1);
    }

    @Test
    void toDto_handlesNullIdAndNullRole() {
        User user = new User();
        user.setId(null);
        user.setRole(null);
        user.setUsername("u");

        var dto = mapper.toDto(user);

        assertThat(dto.getId()).isZero();
        assertThat(dto.getRole()).isNull();
    }

    @Test
    void encodePassword_returnsNullForNullInput() {
        String encoded = ReflectionTestUtils.invokeMethod(mapper, "encodePassword", (String) null);
        assertThat(encoded).isNull();
    }

    @Test
    void updateUserFromDto_ignoresNullRequest() {
        User user = new User();
        user.setName("before");

        mapper.updateUserFromDto(null, user);

        assertThat(user.getName()).isEqualTo("before");
    }

    @Test
    void updateUserFromDto_updatesEmailWhenProvided() {
        User user = new User();
        UserUpdateRequest req = new UserUpdateRequest();
        req.setEmail("new@example.com");

        mapper.updateUserFromDto(req, user);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void updateUserFromDto_doesNotTouchRoleAndPunishments() {
        User user = new User();
        user.setRole(Role.ADMIN);
        UserPunishment punishment = new UserPunishment();
        user.getPunishments().add(punishment);

        UserUpdateRequest req = new UserUpdateRequest();
        req.setName("Updated Name");

        mapper.updateUserFromDto(req, user);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getPunishments()).hasSize(1).containsExactly(punishment);
    }

    @Test
    void toDtoList_convertsMapEntriesWithSecurityFlags() {
        List<?> users = List.of(
                java.util.Map.of(
                        "id", 7,
                        "name", "Cached User",
                        "username", "cached",
                        "email", "cached@example.com",
                        "role", "USER",
                        "password", "secret"
                )
        );

        var result = mapper.toDtoList(users);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("cached");
        assertThat(result.get(0).getEmail()).isEqualTo("cached@example.com");
        assertThat(result.get(0).getRole()).isEqualTo("USER");
    }
}

