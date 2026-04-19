package dev.knalis.trajectaapi.model.task;

public enum AnalysisStatus {
    COMPLETED,
    FAILED;
    
    public boolean isSuccess() {
        return this == COMPLETED;
    }
}


