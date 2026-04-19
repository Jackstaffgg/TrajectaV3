package dev.knalis.trajectaapi.service.intrf.task;

import dev.knalis.trajectaapi.dto.messaging.AnalysisResult;
import dev.knalis.trajectaapi.dto.task.TaskComparisonResponse;
import dev.knalis.trajectaapi.dto.task.TaskBulkDeleteResponse;
import dev.knalis.trajectaapi.dto.task.TaskBulkMoveResponse;
import dev.knalis.trajectaapi.model.task.FlightTask;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Core telemetry task lifecycle service.
 */
public interface FlightTaskService {
    /** Creates a new task, uploads source file, and starts analysis. */
    FlightTask createAndStartAnalysis(String title, MultipartFile file, Long userId);

    /** Creates a task with optional folder and analysis parameters. */
    FlightTask createAndStartAnalysis(String title, MultipartFile file, Long userId, String folderName, Map<String, Object> analysisParams);

    /** Returns a task if the authenticated user is authorized to access it. */
    FlightTask getTask(Long taskId, Authentication auth);

    /** Returns paged tasks of the current user. */
    List<FlightTask> getMyTasks(Authentication auth, int offset, int limit);

    /** Completes a task using worker-provided analysis result. */
    FlightTask completeTask(AnalysisResult result);

    /** Resolves source BIN object key for an authorized user. */
    String getTaskRawKey(Long taskId, Authentication auth);

    /** Resolves source BIN object key for internal worker flow. */
    String getTaskRawKeyInternal(Long taskId);

    /** Resolves trajectory JSON object key for an authorized user. */
    String getTaskTrajectoryKey(Long taskId, Authentication auth);

    /** Appends AI conclusion to trajectory output if not already added. */
    FlightTask addAiConclusion(Long taskId, Authentication auth);

    /** Forces AI conclusion regeneration for a task. */
    FlightTask regenerateAiConclusion(Long taskId, Authentication auth);

    /** Deletes tasks available to current user in a single request. */
    TaskBulkDeleteResponse deleteTasks(List<Long> taskIds, Authentication auth);

    /** Moves selected tasks to target folder (or root when folderId is null). */
    TaskBulkMoveResponse moveTasksToFolder(List<Long> taskIds, Long folderId, Authentication auth);

    /** Clones task input into a new task and starts analysis with optional parameter overrides. */
    FlightTask reanalyzeTask(Long sourceTaskId, String title, String folderName, Map<String, Object> analysisParams, Authentication auth);

    /** Returns comparison rows for selected task metrics in a UI-ready format. */
    TaskComparisonResponse compareTasks(List<Long> taskIds, Long baselineTaskId, Authentication auth);
}


