package dev.knalis.trajectaapi.repo;

import dev.knalis.trajectaapi.model.task.FlightMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightMetricsRepository extends JpaRepository<FlightMetrics, Long> {
	void deleteByTaskId(Long taskId);

	java.util.List<FlightMetrics> findByTaskIdIn(java.util.List<Long> taskIds);
}


