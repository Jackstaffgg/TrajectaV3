package dev.knalis.trajectaapi.mapper;

import dev.knalis.trajectaapi.dto.messaging.AnalysisMetrics;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class FlightMetricMapperTest {

    private final FlightMetricMapper mapper = Mappers.getMapper(FlightMetricMapper.class);

    @Test
    void mapMetrics_mapsAllFields() {
        AnalysisMetrics metrics = new AnalysisMetrics();
        metrics.setMaxAltitude(1.0);
        metrics.setMaxSpeed(2.0);
        metrics.setFlightDuration(3.0);
        metrics.setDistance(4.0);

        var entity = mapper.mapMetrics(15L, metrics);

        assertThat(entity.getTaskId()).isEqualTo(15L);
        assertThat(entity.getMaxAltitude()).isEqualTo(1.0);
        assertThat(entity.getMaxSpeed()).isEqualTo(2.0);
        assertThat(entity.getFlightDuration()).isEqualTo(3.0);
        assertThat(entity.getDistance()).isEqualTo(4.0);
    }

    @Test
    void mapMetrics_returnsNullWhenBothInputsNull() {
        assertThat(mapper.mapMetrics(null, null)).isNull();
    }

    @Test
    void mapMetrics_handlesNullMetricsButKeepsTaskId() {
        var entity = mapper.mapMetrics(33L, null);

        assertThat(entity).isNotNull();
        assertThat(entity.getTaskId()).isEqualTo(33L);
        assertThat(entity.getMaxAltitude()).isNull();
    }

    @Test
    void mapMetrics_handlesNullTaskId() {
        AnalysisMetrics metrics = new AnalysisMetrics();
        metrics.setMaxSpeed(99.0);

        var entity = mapper.mapMetrics(null, metrics);

        assertThat(entity).isNotNull();
        assertThat(entity.getTaskId()).isNull();
        assertThat(entity.getMaxSpeed()).isEqualTo(99.0);
    }
}

