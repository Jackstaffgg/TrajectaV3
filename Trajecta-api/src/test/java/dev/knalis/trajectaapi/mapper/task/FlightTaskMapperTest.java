package dev.knalis.trajectaapi.mapper.task;

import dev.knalis.trajectaapi.model.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class FlightTaskMapperTest {

    private final FlightTaskMapper mapper = Mappers.getMapper(FlightTaskMapper.class);

    @Test
    void toEntity_setsDefaults() {
        var task = mapper.toEntity("Job", 42L);

        assertThat(task.getId()).isNull();
        assertThat(task.getTitle()).isEqualTo("Job");
        assertThat(task.getUserId()).isEqualTo(42L);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
        assertThat(task.getRawLogObjectKey()).isNull();
    }

    @Test
    void toEntity_returnsNullWhenBothInputsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_handlesNullTitleWithUserId() {
        var task = mapper.toEntity(null, 5L);

        assertThat(task).isNotNull();
        assertThat(task.getTitle()).isNull();
        assertThat(task.getUserId()).isEqualTo(5L);
    }
}

