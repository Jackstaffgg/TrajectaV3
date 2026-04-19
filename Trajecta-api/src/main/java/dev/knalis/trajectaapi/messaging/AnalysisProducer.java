package dev.knalis.trajectaapi.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisProducer {

    private final RabbitTemplate rabbitTemplate;
    
    public void sendForAnalysis(Long taskId) {
        sendForAnalysis(taskId, Map.of());
    }

    public void sendForAnalysis(Long taskId, Map<String, Object> analysisParams) {
        Double timelineDtSeconds = null;
        if (analysisParams != null) {
            Object raw = analysisParams.get("timelineDtSeconds");
            if (raw instanceof Number number) {
                timelineDtSeconds = number.doubleValue();
            }
        }

        rabbitTemplate.convertAndSend(
                RabbitTopology.EXCHANGE,
                RabbitTopology.PARSE_ROUTING_KEY,
                new AnalysisRequest(taskId, timelineDtSeconds)
        );
    }
    
    @Data
    @AllArgsConstructor
    public static class AnalysisRequest {
        private Long taskId;
        private Double timelineDtSeconds;
    }
}


