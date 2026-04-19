package dev.knalis.trajectaapi.event.analys;

import dev.knalis.trajectaapi.messaging.AnalysisProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AnalysisRequestedEventListener {
    
    private final AnalysisProducer analysisProducer;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnalysisRequestedEvent event) {
        analysisProducer.sendForAnalysis(event.getTaskId(), event.getAnalysisParams());
    }
}


