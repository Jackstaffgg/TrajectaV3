package dev.knalis.trajectaapi.mapper;

import dev.knalis.trajectaapi.dto.user.UserPunishmentResponse;
import dev.knalis.trajectaapi.model.user.punishment.UserPunishment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserPunishmentMapper {
    
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "punishedById", source = "punishedBy.id")
    @Mapping(target = "expired", expression = "java(userPunishment.isExpired())")
    UserPunishmentResponse toDto(UserPunishment userPunishment);
}