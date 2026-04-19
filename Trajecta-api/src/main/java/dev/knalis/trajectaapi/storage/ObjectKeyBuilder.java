package dev.knalis.trajectaapi.storage;

import org.springframework.stereotype.Component;

@Component
public class ObjectKeyBuilder {
    
    public String buildRawLogKey(Long userId, Long taskId) {
        return "tasks/" + userId + "/" + taskId + "/raw/source.bin";
    }
    
    public String buildTrajectoryKey(Long userId, Long taskId) {
        return "tasks/" + userId + "/" + taskId + "/result/trajectory.json";
    }
}


