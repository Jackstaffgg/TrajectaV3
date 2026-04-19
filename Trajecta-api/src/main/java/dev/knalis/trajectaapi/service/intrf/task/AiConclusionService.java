package dev.knalis.trajectaapi.service.intrf.task;

import dev.knalis.trajectaapi.dto.task.AiConclusionGenerationResult;

/**
 * Generates a concise AI conclusion for analyzed trajectory content.
 */
public interface AiConclusionService {
    AiConclusionGenerationResult generateConclusion(String trajectoryContent);
}


