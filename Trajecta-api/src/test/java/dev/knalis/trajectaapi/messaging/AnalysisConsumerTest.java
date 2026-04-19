package dev.knalis.trajectaapi.messaging;

import dev.knalis.trajectaapi.dto.messaging.AnalysisResult;
import dev.knalis.trajectaapi.exception.NotFoundException;
import dev.knalis.trajectaapi.model.task.AnalysisStatus;
import dev.knalis.trajectaapi.service.intrf.task.FlightTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisConsumerTest {

    @Mock
    private FlightTaskService taskService;

    private AnalysisConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AnalysisConsumer(taskService);
    }

    @Test
    void handleAnalysisResult_callsCompleteTask_forValidPayload() {
        AnalysisResult result = new AnalysisResult();
        result.setTaskId(42L);

        consumer.handleAnalysisResult(result);

        verify(taskService).completeTask(result);
    }

    @Test
    void handleAnalysisResult_ignoresNullPayload() {
        consumer.handleAnalysisResult(null);

        verify(taskService, never()).completeTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleAnalysisResult_ignoresPayloadWithNullTaskId() {
        AnalysisResult result = new AnalysisResult();

        consumer.handleAnalysisResult(result);

        verify(taskService, never()).completeTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleAnalysisResult_ignoresStaleResultWhenTaskMissing() {
        AnalysisResult result = new AnalysisResult();
        result.setTaskId(100L);
        doThrow(new NotFoundException("Task not found")).when(taskService).completeTask(result);

        consumer.handleAnalysisResult(result);

        verify(taskService).completeTask(result);
    }

    @Test
    void handleAnalysisResult_marksFailedWhenUnexpectedErrorOccurs() {
        AnalysisResult result = new AnalysisResult();
        result.setTaskId(55L);

        doThrow(new RuntimeException("boom"))
                .doReturn(null)
                .when(taskService)
                .completeTask(any(AnalysisResult.class));

        consumer.handleAnalysisResult(result);

        ArgumentCaptor<AnalysisResult> captor = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(taskService, times(2)).completeTask(captor.capture());
        AnalysisResult fallback = captor.getAllValues().get(1);
        org.junit.jupiter.api.Assertions.assertEquals(55L, fallback.getTaskId());
        org.junit.jupiter.api.Assertions.assertEquals(AnalysisStatus.FAILED, fallback.getStatus());
    }
}
