package dev.knalis.trajectaapi.mapper.task;

import dev.knalis.trajectaapi.model.task.FlightTask;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import dev.knalis.trajectaapi.model.task.ai.AiConclusion;
import dev.knalis.trajectaapi.model.task.ai.AiModel;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskResponseMapperTest {

    private final TaskResponseMapper mapper = Mappers.getMapper(TaskResponseMapper.class);

    @Test
    void toCreateResponse_usesStatusName() {
        FlightTask task = new FlightTask();
        task.setId(9L);
        task.setTitle("T");
        task.setStatus(TaskStatus.PROCESSING);

        var response = mapper.toCreateResponse(task);

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void toDto_andToCreateResponse_returnNullForNullInput() {
        assertThat(mapper.toDto(null)).isNull();
        assertThat(mapper.toCreateResponse(null)).isNull();
    }

    @Test
    void toDtoList_mapsAndHandlesNullInput() {
        FlightTask task = new FlightTask();
        task.setId(1L);
        task.setStatus(TaskStatus.COMPLETED);

        assertThat(mapper.toDtoList(List.of(task))).hasSize(1);
        assertThat(mapper.toDtoList(null)).isNull();
    }

    @Test
    void toDto_mapsAiConclusion() {
        FlightTask task = new FlightTask();
        task.setId(3L);
        task.setStatus(TaskStatus.COMPLETED);

        AiConclusion aiConclusion = new AiConclusion();
        aiConclusion.setConclusion("Stable flight");
        aiConclusion.setAiModel(AiModel.GPT_4O_MINI);
        task.setAiConclusion(aiConclusion);

        var dto = mapper.toDto(task);

        assertThat(dto.getAiConclusion()).isEqualTo("Stable flight");
        assertThat(dto.getAiModel()).isEqualTo("GPT_4O_MINI");
    }
}

