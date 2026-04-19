package dev.knalis.trajectaapi.mapper;

import dev.knalis.trajectaapi.dto.notification.NotificationResponse;
import dev.knalis.trajectaapi.model.notiffication.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponse toDto(Notification notification);

    List<NotificationResponse> toDtoList(List<Notification> notifications);
}



