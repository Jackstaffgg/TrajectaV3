package dev.knalis.trajectaapi.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.trajectaapi.dto.user.AdminUserDetailsResponse;
import dev.knalis.trajectaapi.dto.user.UserResponse;
import dev.knalis.trajectaapi.dto.user.UserUpdateRequest;
import dev.knalis.trajectaapi.model.user.User;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class UserMapper {
    
    @Autowired
    protected PasswordEncoder passwordEncoder;
    
    @Autowired
    protected ObjectMapper objectMapper;
    
    @Mapping(target = "password", qualifiedByName = "encodePassword")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "punishments", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateUserFromDto(UserUpdateRequest request, @MappingTarget User user);
    
    @Mapping(target = "hasActiveBan", ignore = true)
    public abstract UserResponse toDto(User user);
    
    public List<UserResponse> toDtoList(List<?> users) {
        if (users == null) {
            return null;
        }
        if (users.isEmpty()) {
            return List.of();
        }
        
        List<UserResponse> result = new ArrayList<>(users.size());
        for (Object entry : users) {
            User user;
            if (entry instanceof User casted) {
                user = casted;
            } else if (entry instanceof Map<?, ?>) {
                user = objectMapper.convertValue(entry, User.class);
            } else {
                throw new IllegalArgumentException("Unsupported user list entry type: " + entry.getClass());
            }
            result.add(toDto(user));
        }
        return result;
    }
    
    @Named("encodePassword")
    protected String encodePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }
        return passwordEncoder.encode(rawPassword);
    }
    
    @Mapping(target = "activePunishments", ignore = true)
    @Mapping(target = "punishmentHistory", ignore = true)
    public abstract AdminUserDetailsResponse toAdminDetailsDto(User user);
}
