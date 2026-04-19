package dev.knalis.trajectaapi.mapper;

import dev.knalis.trajectaapi.dto.messaging.AnalysisMetrics;
import dev.knalis.trajectaapi.model.task.FlightMetrics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FlightMetricMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "taskId", source = "taskId")
    @Mapping(target = "maxAltitude", source = "metrics.maxAltitude")
    @Mapping(target = "maxSpeed", source = "metrics.maxSpeed")
    @Mapping(target = "flightDuration", source = "metrics.flightDuration")
    @Mapping(target = "distance", source = "metrics.distance")
    @Mapping(target = "maxVerticalSpeed", source = "metrics.maxVerticalSpeed")
    @Mapping(target = "endpointErrorM", source = "metrics.endpointErrorM")
    @Mapping(target = "rmsCrosstrackErrorM", source = "metrics.rmsCrosstrackErrorM")
    @Mapping(target = "ateM", source = "metrics.ateM")
    @Mapping(target = "p95CrosstrackErrorM", source = "metrics.p95CrosstrackErrorM")
    @Mapping(target = "pointCount", source = "metrics.pointCount")
    @Mapping(target = "groundTruthUsed", source = "metrics.groundTruthUsed")
    FlightMetrics mapMetrics(Long taskId, AnalysisMetrics metrics);
}


