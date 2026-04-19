package dev.knalis.trajectaapi.mapper.task;

import dev.knalis.trajectaapi.model.task.FlightTask;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = TaskStatus.class)
public interface FlightTaskMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "rawLogObjectKey", ignore = true)
    @Mapping(target = "status", expression = "java(TaskStatus.PROCESSING)")
    @Mapping(target = "trajectoryObjectKey", ignore = true)
    @Mapping(target = "folderName", ignore = true)
    @Mapping(target = "folderId", ignore = true)
    @Mapping(target = "analysisParamsJson", ignore = true)
    @Mapping(target = "sourceTaskId", ignore = true)
    @Mapping(target = "hasAiConclusion", constant = "false")
    @Mapping(target = "aiConclusion", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "finishedAt", ignore = true)
    FlightTask toEntity(String title, Long userId);
}
