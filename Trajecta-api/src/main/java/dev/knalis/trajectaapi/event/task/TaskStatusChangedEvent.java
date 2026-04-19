package dev.knalis.trajectaapi.event.task;

import dev.knalis.trajectaapi.model.task.TaskStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskStatusChangedEvent {
    
    private final Long taskId;
    private final Long userId;
    private final String taskTitle;
    private final TaskStatus taskStatus;
    private final String message;
}


