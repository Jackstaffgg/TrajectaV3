package dev.knalis.trajectaapi.event;

import dev.knalis.trajectaapi.event.analys.AnalysisRequestedEvent;
import dev.knalis.trajectaapi.event.task.TaskStatusChangedEvent;
import dev.knalis.trajectaapi.event.user.UserBannedEvent;
import dev.knalis.trajectaapi.event.user.UserUnbannedEvent;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public void publishTaskStatusChanged(Long taskId, Long userId, String title, TaskStatus status, String message) {
        eventPublisher.publishEvent(TaskStatusChangedEvent.builder().taskId(taskId).userId(userId).taskTitle(title).taskStatus(status).message(message).build());
    }
    
    public void publishAnalysisRequested(Long taskId) {
        eventPublisher.publishEvent(new AnalysisRequestedEvent(taskId));
    }

    public void publishAnalysisRequested(Long taskId, Map<String, Object> analysisParams) {
        eventPublisher.publishEvent(new AnalysisRequestedEvent(taskId, analysisParams == null ? Map.of() : analysisParams));
    }
    
    public void publishUserBanned(
            long userId,
            long punishmentId,
            String reason,
            Instant expiredAt,
            long punishedById,
            String punishedByName
    ) {
        eventPublisher.publishEvent(new UserBannedEvent(
                userId,
                punishmentId,
                reason,
                expiredAt,
                punishedById,
                punishedByName
        ));
    }
    
    public void publishUserUnbanned(long userId) {
        eventPublisher.publishEvent(new UserUnbannedEvent(userId));
    }
}


