package dev.knalis.trajectaapi.mapper.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.trajectaapi.dto.task.TaskCreateResponse;
import dev.knalis.trajectaapi.dto.task.TaskResponse;
import dev.knalis.trajectaapi.model.task.FlightTask;
import dev.knalis.trajectaapi.model.task.ai.AiConclusion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class TaskResponseMapper {
    
    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "folderName", source = "folderName")
    @Mapping(target = "folderId", source = "folderId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "errorMessage", source = "errorMessage")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "finishedAt", source = "finishedAt")
    @Mapping(target = "aiConclusion", expression = "java(extractAiConclusion(task))")
    @Mapping(target = "aiModel", expression = "java(extractAiModel(task))")
    @Mapping(target = "analysisParams", expression = "java(extractAnalysisParams(task))")
    public abstract TaskResponse toDto(FlightTask task);

    protected String extractAiConclusion(FlightTask task) {
        if (task == null) {
            return null;
        }

        AiConclusion aiConclusion = task.getAiConclusion();
        return aiConclusion != null ? aiConclusion.getConclusion() : null;
    }

    protected String extractAiModel(FlightTask task) {
        if (task == null || task.getAiConclusion() == null || task.getAiConclusion().getAiModel() == null) {
            return null;
        }
        return task.getAiConclusion().getAiModel().name();
    }

    protected Map<String, Object> extractAnalysisParams(FlightTask task) {
        if (task == null || task.getAnalysisParamsJson() == null || task.getAnalysisParamsJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(task.getAnalysisParamsJson(), Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public List<TaskResponse> toDtoList(List<?> tasks) {
        if (tasks == null) {
            return null;
        }
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<TaskResponse> result = new ArrayList<>(tasks.size());
        for (Object entry : tasks) {
            FlightTask task;
            if (entry instanceof FlightTask casted) {
                task = casted;
            } else if (entry instanceof Map<?, ?>) {
                task = objectMapper.convertValue(entry, FlightTask.class);
            } else {
                throw new IllegalArgumentException("Unsupported task list entry type: " + entry.getClass());
            }
            result.add(toDto(task));
        }
        return result;
    }
    
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "folderName", source = "folderName")
    @Mapping(target = "folderId", source = "folderId")
    @Mapping(target = "status", expression = "java(task.getStatus().name())")
    @Mapping(target = "sourceTaskId", source = "sourceTaskId")
    public abstract TaskCreateResponse toCreateResponse(FlightTask task);
}
