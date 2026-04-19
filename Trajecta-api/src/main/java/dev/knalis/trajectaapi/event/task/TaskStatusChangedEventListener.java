package dev.knalis.trajectaapi.event.task;

import dev.knalis.trajectaapi.dto.notification.NotificationCreateRequest;
import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.dto.ws.payload.TaskUpdateStatusPayload;
import dev.knalis.trajectaapi.model.notiffication.NotificationType;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import dev.knalis.trajectaapi.service.intrf.WsEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskStatusChangedEventListener {
    
    private final WsEventDispatcher wsEventDispatcher;
    private final NotificationService notificationService;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TaskStatusChangedEvent event) {
        TaskUpdateStatusPayload payload = TaskUpdateStatusPayload.builder()
                .taskId(event.getTaskId())
                .taskStatus(event.getTaskStatus())
                .taskTitle(event.getTaskTitle())
                .message(event.getMessage())
                .build();
        
        wsEventDispatcher.emitToUsers(
                List.of(event.getUserId()),
                WsEventType.TASK_STATUS_UPDATE,
                payload
        );

        createTaskNotification(event);
    }

    private void createTaskNotification(TaskStatusChangedEvent event) {
        NotificationType type;
        String content;

        if (event.getTaskStatus() == TaskStatus.COMPLETED) {
            type = NotificationType.TASK_COMPLETED;
            content = "Task #" + event.getTaskId() + " completed: " + event.getTaskTitle();
        } else if (event.getTaskStatus() == TaskStatus.FAILED) {
            type = NotificationType.TASK_FAILED;
            content = "Task #" + event.getTaskId() + " failed" + (event.getMessage() != null ? ": " + event.getMessage() : "");
        } else if (event.getTaskStatus() == TaskStatus.CANCELLED) {
            type = NotificationType.SYSTEM_ALERT;
            content = "Task #" + event.getTaskId() + " was cancelled";
        } else if (event.getTaskStatus() == TaskStatus.PROCESSING) {
            type = NotificationType.SYSTEM_ALERT;
            content = "Task #" + event.getTaskId() + " started processing";
        } else {
            return;
        }

        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setRecipientId(event.getUserId());
        request.setType(type);
        request.setContent(content);
        request.setReferenceId(event.getTaskId());
        notificationService.createNotification(request);
    }
}


