package dev.knalis.trajectaapi.mapper;

import dev.knalis.trajectaapi.model.notiffication.Notification;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private final NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);

    @Test
    void toDto_mapsBasicFields() {
        Notification n = new Notification();
        n.setId(3L);
        n.setType(NotificationType.SYSTEM_ALERT);
        n.setContent("hello");
        n.setSenderId(7L);
        n.setSenderName("bot");
        n.setRead(true);

        var dto = mapper.toDto(n);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getType()).isEqualTo(NotificationType.SYSTEM_ALERT);
        assertThat(dto.getContent()).isEqualTo("hello");
        assertThat(dto.isRead()).isTrue();
    }

    @Test
    void toDto_returnsNullForNullInput() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDtoList_mapsAndHandlesNullList() {
        Notification n = new Notification();
        n.setId(10L);

        assertThat(mapper.toDtoList(List.of(n))).hasSize(1);
        assertThat(mapper.toDtoList(null)).isNull();
    }
}

