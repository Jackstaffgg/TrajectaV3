package dev.knalis.trajectaapi.event.analys;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class AnalysisRequestedEvent {
    private final Long taskId;
    private final Map<String, Object> analysisParams;

    public AnalysisRequestedEvent(Long taskId) {
        this(taskId, Map.of());
    }
}


