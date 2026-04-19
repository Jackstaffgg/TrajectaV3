package dev.knalis.trajectaapi.event.task;

import dev.knalis.trajectaapi.dto.ws.WsEventType;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import dev.knalis.trajectaapi.service.intrf.NotificationService;
import dev.knalis.trajectaapi.service.intrf.WsEventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskStatusChangedEventListenerTest {

    @Mock
    private WsEventDispatcher wsEventDispatcher;
    @Mock
    private NotificationService notificationService;

    private TaskStatusChangedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TaskStatusChangedEventListener(wsEventDispatcher, notificationService);
    }

    @Test
    void handle_createsNotificationForCompletedStatus() {
        TaskStatusChangedEvent event = TaskStatusChangedEvent.builder()
                .taskId(5L)
                .userId(9L)
                .taskTitle("demo")
                .taskStatus(TaskStatus.COMPLETED)
                .build();

        listener.handle(event);

        verify(wsEventDispatcher).emitToUsers(any(), eq(WsEventType.TASK_STATUS_UPDATE), any());
        verify(notificationService).createNotification(any());
    }

    @Test
    void handle_skipsNotificationForPendingStatus() {
        TaskStatusChangedEvent event = TaskStatusChangedEvent.builder()
                .taskId(6L)
                .userId(9L)
                .taskTitle("demo")
                .taskStatus(TaskStatus.PENDING)
                .build();

        listener.handle(event);

        verify(wsEventDispatcher).emitToUsers(any(), eq(WsEventType.TASK_STATUS_UPDATE), any());
        verify(notificationService, never()).createNotification(any());
    }
}

