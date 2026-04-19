package dev.knalis.trajectaapi.messaging;

import dev.knalis.trajectaapi.dto.messaging.AnalysisResult;
import dev.knalis.trajectaapi.exception.NotFoundException;
import dev.knalis.trajectaapi.model.task.AnalysisStatus;
import dev.knalis.trajectaapi.service.intrf.task.FlightTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisConsumer {

    private final FlightTaskService taskService;

    @RabbitListener(queues = RabbitTopology.RESULTS_QUEUE)
    public void handleAnalysisResult(AnalysisResult result) {
        if (result == null || result.getTaskId() == null) {
            log.warn("Skipping invalid analysis result message: payload or taskId is null");
            return;
        }

        log.info("Received analysis result for task: {}", result.getTaskId());
        try {
            taskService.completeTask(result);
        } catch (NotFoundException ex) {
            log.warn("Skipping stale analysis result for missing task {}", result.getTaskId());
        } catch (Exception ex) {
            log.error("Unexpected error while applying analysis result for task {}", result.getTaskId(), ex);
            try {
                AnalysisResult fallback = new AnalysisResult();
                fallback.setTaskId(result.getTaskId());
                fallback.setStatus(AnalysisStatus.FAILED);
                fallback.setErrorMessage("Failed to finalize analysis result: " + ex.getClass().getSimpleName());
                taskService.completeTask(fallback);
            } catch (Exception fallbackEx) {
                log.error("Fallback task failure marking failed for task {}", result.getTaskId(), fallbackEx);
            }
        }
    }
}


