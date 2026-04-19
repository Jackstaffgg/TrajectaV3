package dev.knalis.trajectaapi.dto.task;

import dev.knalis.trajectaapi.model.task.ai.AiModel;

public record AiConclusionGenerationResult(
        String conclusion,
        AiModel model,
        String errorMessage
) {
}

