package dev.knalis.trajectaapi.model.task;

public enum TaskStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED;
    
    public boolean isTerminal() {
        return this == COMPLETED ||
                this == FAILED ||
                this == CANCELLED;
    }
}


