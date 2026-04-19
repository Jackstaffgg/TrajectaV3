package dev.knalis.trajectaapi.factory;

import dev.knalis.trajectaapi.mapper.task.FlightTaskMapper;
import dev.knalis.trajectaapi.model.task.FlightTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlightTaskFactory {
    
    private final FlightTaskMapper mapper;
    
    public FlightTask create(String title, Long userId) {
        return mapper.toEntity(title, userId);
    }
}


