package dev.knalis.trajectaapi.repo;

import dev.knalis.trajectaapi.model.task.ai.AiConclusion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConclusionRepository extends JpaRepository<AiConclusion, Long> {
	Optional<AiConclusion> findByFlightTaskId(Long taskId);
}

