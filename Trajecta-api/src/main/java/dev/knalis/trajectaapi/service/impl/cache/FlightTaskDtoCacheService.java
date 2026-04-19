package dev.knalis.trajectaapi.service.impl.cache;

import dev.knalis.trajectaapi.dto.task.TaskResponse;
import dev.knalis.trajectaapi.mapper.task.TaskResponseMapper;
import dev.knalis.trajectaapi.service.intrf.task.FlightTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FlightTaskDtoCacheService {

    private final FlightTaskService flightTaskService;
    private final TaskResponseMapper taskResponseMapper;

    @Cacheable(cacheNames = "taskDtoByIdAndUserV3", key = "#taskId + ':' + #username")
    public TaskResponse getTaskDto(Long taskId, String username, Authentication auth) {
        Objects.requireNonNull(username, "username must not be null");
        return taskResponseMapper.toDto(flightTaskService.getTask(taskId, auth));
    }

    @Cacheable(cacheNames = "taskDtoByUserPageV3", key = "#username + ':' + #offset + ':' + #limit")
    public List<TaskResponse> getMyTaskDtos(String username, int offset, int limit, Authentication auth) {
        Objects.requireNonNull(username, "username must not be null");
        return taskResponseMapper.toDtoList(flightTaskService.getMyTasks(auth, offset, limit));
    }
}

