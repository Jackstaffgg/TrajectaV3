package dev.knalis.trajectaapi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.trajectaapi.dto.messaging.AnalysisMetrics;
import dev.knalis.trajectaapi.dto.messaging.AnalysisResult;
import dev.knalis.trajectaapi.dto.task.AiConclusionGenerationResult;
import dev.knalis.trajectaapi.event.EventPublisher;
import dev.knalis.trajectaapi.exception.BadRequestException;
import dev.knalis.trajectaapi.exception.NotFoundException;
import dev.knalis.trajectaapi.exception.PermissionDeniedException;
import dev.knalis.trajectaapi.exception.RateLimitException;
import dev.knalis.trajectaapi.factory.FlightTaskFactory;
import dev.knalis.trajectaapi.mapper.FlightMetricMapper;
import dev.knalis.trajectaapi.model.task.AnalysisStatus;
import dev.knalis.trajectaapi.model.task.FlightMetrics;
import dev.knalis.trajectaapi.model.task.FlightTask;
import dev.knalis.trajectaapi.model.task.TaskStatus;
import dev.knalis.trajectaapi.model.task.ai.AiConclusion;
import dev.knalis.trajectaapi.model.task.ai.AiModel;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.repo.AiConclusionRepository;
import dev.knalis.trajectaapi.repo.FlightMetricsRepository;
import dev.knalis.trajectaapi.repo.FlightTaskRepository;
import dev.knalis.trajectaapi.security.AiConclusionRateLimiter;
import dev.knalis.trajectaapi.security.TaskCreationRateLimiter;
import dev.knalis.trajectaapi.service.impl.task.FlightTaskServiceImpl;
import dev.knalis.trajectaapi.service.intrf.task.AiConclusionService;
import dev.knalis.trajectaapi.service.intrf.task.FileService;
import dev.knalis.trajectaapi.storage.ObjectKeyBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightTaskServiceImplTest {

    @Mock
    private FlightTaskRepository taskRepository;
    @Mock
    private FileService fileService;
    @Mock
    private FlightMetricsRepository metricsRepository;
    @Mock
    private AiConclusionRepository aiConclusionRepository;
    @Mock
    private AiConclusionRateLimiter aiConclusionRateLimiter;
    @Mock
    private TaskCreationRateLimiter taskCreationRateLimiter;
    @Mock
    private FlightMetricMapper flightMetricMapper;
    @Mock
    private FlightTaskFactory taskFactory;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ObjectKeyBuilder objectKeyBuilder;
    @Mock
    private AiConclusionService aiConclusionService;
    @Mock
    private Authentication authentication;

    private FlightTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlightTaskServiceImpl(
                taskRepository,
                fileService,
                metricsRepository,
                aiConclusionRepository,
                aiConclusionRateLimiter,
                taskCreationRateLimiter,
                flightMetricMapper,
                taskFactory,
                eventPublisher,
                objectKeyBuilder,
                new ObjectMapper(),
                aiConclusionService
        );
    }

    @Test
    void createAndStartAnalysis_success() {
        FlightTask created = new FlightTask();
        created.setTitle("task");
        created.setUserId(1L);

        FlightTask saved = new FlightTask();
        saved.setId(10L);
        saved.setTitle("task");
        saved.setUserId(1L);
        saved.setStatus(TaskStatus.PROCESSING);

        when(taskCreationRateLimiter.isAllowed(1L)).thenReturn(true);
        when(taskFactory.create("task", 1L)).thenReturn(created);
        when(taskRepository.save(created)).thenReturn(saved);
        when(objectKeyBuilder.buildRawLogKey(1L, 10L)).thenReturn("tasks/1/10/raw/source.bin");
        when(taskRepository.save(saved)).thenReturn(saved);

        var file = new MockMultipartFile("file", "source.bin", "application/octet-stream", new byte[]{1, 2, 3});

        FlightTask result = service.createAndStartAnalysis("task", file, 1L);

        assertThat(result.getRawLogObjectKey()).isEqualTo("tasks/1/10/raw/source.bin");
        verify(fileService).uploadToKey("tasks/1/10/raw/source.bin", file);
        verify(eventPublisher).publishAnalysisRequested(10L, Map.of());
        verify(taskCreationRateLimiter).onAttempt(1L);
    }

    @Test
    void createAndStartAnalysis_rateLimitExceeded() {
        when(taskCreationRateLimiter.isAllowed(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.createAndStartAnalysis("x", new MockMultipartFile("f", new byte[]{1}), 7L))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void getTask_deniesAccessForAnotherUser() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(22L);
        task.setUserId(2L);

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(22L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.getTask(22L, authentication))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void getMyTasks_returnsPagedContent() {
        User current = new User();
        current.setId(9L);

        FlightTask task = new FlightTask();
        task.setId(1L);

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findByUserIdOrderByCreatedAtDesc(eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of(task)));

        List<FlightTask> result = service.getMyTasks(authentication, 0, 20);

        assertThat(result).hasSize(1);
    }

    @Test
    void completeTask_marksCompleted_andSavesMetrics() {
        FlightTask task = new FlightTask();
        task.setId(3L);
        task.setUserId(8L);
        task.setStatus(TaskStatus.PROCESSING);
        task.setTitle("a");

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(3L);
        result.setStatus(AnalysisStatus.COMPLETED);
        result.setTrajectoryObjectKey("tasks/8/3/result/trajectory.json");

        AnalysisMetrics metrics = new AnalysisMetrics();
        metrics.setMaxAltitude(100.0);
        result.setMetrics(metrics);

        FlightMetrics mapped = new FlightMetrics();

        when(taskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(flightMetricMapper.mapMetrics(3L, metrics)).thenReturn(mapped);
        when(taskRepository.save(task)).thenReturn(task);

        FlightTask saved = service.completeTask(result);

        assertThat(saved.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(metricsRepository).save(mapped);
        verify(eventPublisher).publishTaskStatusChanged(eq(3L), eq(8L), eq("a"), eq(TaskStatus.COMPLETED), isNull());
    }

    @Test
    void completeTask_uploadsTrajectoryJsonWhenKeyMissing() {
        FlightTask task = new FlightTask();
        task.setId(31L);
        task.setUserId(15L);
        task.setStatus(TaskStatus.PROCESSING);

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(31L);
        result.setStatus(AnalysisStatus.COMPLETED);
        result.setTrajectoryJson("{\"points\":[1]}");

        when(taskRepository.findById(31L)).thenReturn(Optional.of(task));
        when(objectKeyBuilder.buildTrajectoryKey(15L, 31L)).thenReturn("tasks/15/31/result/trajectory.json");
        when(taskRepository.save(task)).thenReturn(task);

        service.completeTask(result);

        verify(fileService).uploadJson("tasks/15/31/result/trajectory.json", "{\"points\":[1]}");
        assertThat(task.getTrajectoryObjectKey()).isEqualTo("tasks/15/31/result/trajectory.json");
    }

    @Test
    void getTaskRawKeyInternal_throwsWhenMissing() {
        FlightTask task = new FlightTask();
        task.setId(1L);
        task.setRawLogObjectKey(null);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.getTaskRawKeyInternal(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTaskRawKeyInternal_returnsKeyWhenPresent() {
        FlightTask task = new FlightTask();
        task.setId(2L);
        task.setRawLogObjectKey("tasks/1/2/raw/source.bin");
        when(taskRepository.findById(2L)).thenReturn(Optional.of(task));

        assertThat(service.getTaskRawKeyInternal(2L)).isEqualTo("tasks/1/2/raw/source.bin");
    }

    @Test
    void getTaskRawKeyInternal_throwsWhenTaskMissing() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTaskRawKeyInternal(404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addAiConclusion_appendsAndMarks() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(5L);
        task.setUserId(1L);
        task.setTrajectoryObjectKey("tasks/1/5/result/trajectory.json");

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(true);
        when(fileService.getObjectStream(task.getTrajectoryObjectKey()))
                .thenReturn(new ByteArrayInputStream("{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
        when(aiConclusionService.generateConclusion("{\"a\":1}"))
                .thenReturn(new AiConclusionGenerationResult("Detected stable flight profile", AiModel.GPT_4O_MINI, null));
        when(aiConclusionRepository.save(any(AiConclusion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(task)).thenReturn(task);

        FlightTask saved = service.addAiConclusion(5L, authentication);

        assertThat(saved.getAiConclusion()).isNotNull();
        assertThat(saved.getAiConclusion().getConclusion()).isEqualTo("Detected stable flight profile");
        assertThat(saved.getAiConclusion().getAiModel()).isEqualTo(AiModel.GPT_4O_MINI);
        verify(fileService).uploadJson(eq("tasks/1/5/result/trajectory.json"), contains("aiConclusion"));
    }

    @Test
    void addAiConclusion_throwsOnIoFailure() {
        User current = new User();
        current.setId(2L);

        FlightTask task = new FlightTask();
        task.setId(6L);
        task.setUserId(2L);
        task.setTrajectoryObjectKey("tasks/2/6/result/trajectory.json");

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(6L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(2L)).thenReturn(true);
        when(fileService.getObjectStream(task.getTrajectoryObjectKey())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.addAiConclusion(6L, authentication))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getMyTasks_invalidPagination() {
        assertThatThrownBy(() -> service.getMyTasks(authentication, 1, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createAndStartAnalysis_deletesUploadedObjectWhenPublishingFails() {
        FlightTask created = new FlightTask();
        FlightTask saved = new FlightTask();
        saved.setId(11L);
        saved.setUserId(1L);
        saved.setTitle("task");
        saved.setStatus(TaskStatus.PROCESSING);

        when(taskCreationRateLimiter.isAllowed(1L)).thenReturn(true);
        when(taskFactory.create("task", 1L)).thenReturn(created);
        when(taskRepository.save(created)).thenReturn(saved);
        when(objectKeyBuilder.buildRawLogKey(1L, 11L)).thenReturn("tasks/1/11/raw/source.bin");
        when(taskRepository.save(saved)).thenReturn(saved);
        doThrow(new RuntimeException("publish fail")).when(eventPublisher)
                .publishTaskStatusChanged(anyLong(), anyLong(), anyString(), any(), any());

        var file = new MockMultipartFile("file", "source.bin", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> service.createAndStartAnalysis("task", file, 1L)).isInstanceOf(RuntimeException.class);
        verify(fileService).delete("tasks/1/11/raw/source.bin");
    }

    @Test
    void getTask_allowsAdminForAnotherUsersTask() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);

        FlightTask task = new FlightTask();
        task.setId(50L);
        task.setUserId(99L);

        when(authentication.getPrincipal()).thenReturn(admin);
        when(taskRepository.findById(50L)).thenReturn(Optional.of(task));

        assertThat(service.getTask(50L, authentication)).isSameAs(task);
    }

    @Test
    void getTask_throwsWhenAuthMissing() {
        assertThatThrownBy(() -> service.getTask(1L, null)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void getTask_throwsWhenPrincipalIsNotUser() {
        when(authentication.getPrincipal()).thenReturn("anonymous");

        assertThatThrownBy(() -> service.getTask(1L, authentication)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void getTask_throwsWhenTaskMissing() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);
        when(authentication.getPrincipal()).thenReturn(user);
        when(taskRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTask(100L, authentication)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTask_allowsOwnerAccess() {
        User user = new User();
        user.setId(9L);
        user.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(101L);
        task.setUserId(9L);

        when(authentication.getPrincipal()).thenReturn(user);
        when(taskRepository.findById(101L)).thenReturn(Optional.of(task));

        assertThat(service.getTask(101L, authentication)).isSameAs(task);
    }

    @Test
    void completeTask_returnsEarlyForTerminalTask() {
        FlightTask task = new FlightTask();
        task.setId(44L);
        task.setStatus(TaskStatus.COMPLETED);

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(44L);
        result.setStatus(AnalysisStatus.COMPLETED);

        when(taskRepository.findById(44L)).thenReturn(Optional.of(task));

        FlightTask returned = service.completeTask(result);

        assertThat(returned).isSameAs(task);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void completeTask_marksFailedWhenNoTrajectoryDataPresent() {
        FlightTask task = new FlightTask();
        task.setId(45L);
        task.setUserId(1L);
        task.setStatus(TaskStatus.PROCESSING);
        task.setTitle("missing-trajectory");

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(45L);
        result.setStatus(AnalysisStatus.COMPLETED);

        when(taskRepository.findById(45L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        service.completeTask(result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).contains("Trajectory file is missing");
    }

    @Test
    void completeTask_marksFailedWhenTrajectoryJsonBlank() {
        FlightTask task = new FlightTask();
        task.setId(47L);
        task.setUserId(1L);
        task.setStatus(TaskStatus.PROCESSING);
        task.setTitle("blank-json");

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(47L);
        result.setStatus(AnalysisStatus.COMPLETED);
        result.setTrajectoryJson("   ");

        when(taskRepository.findById(47L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        service.completeTask(result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void completeTask_marksFailedWhenAnalysisFailed() {
        FlightTask task = new FlightTask();
        task.setId(46L);
        task.setUserId(1L);
        task.setStatus(TaskStatus.PROCESSING);
        task.setTitle("failed");

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(46L);
        result.setStatus(AnalysisStatus.FAILED);
        result.setErrorMessage("worker error");

        when(taskRepository.findById(46L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        service.completeTask(result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("worker error");
    }

    @Test
    void completeTask_marksFailedWhenStatusMissing() {
        FlightTask task = new FlightTask();
        task.setId(460L);
        task.setUserId(1L);
        task.setStatus(TaskStatus.PROCESSING);
        task.setTitle("missing-status");

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(460L);
        result.setStatus(null);

        when(taskRepository.findById(460L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        service.completeTask(result);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getErrorMessage()).contains("status is missing");
    }

    @Test
    void getTask_marksStaleProcessingTaskAsFailed() {
        User current = new User();
        current.setId(50L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(501L);
        task.setUserId(50L);
        task.setTitle("stale");
        task.setStatus(TaskStatus.PROCESSING);
        task.setCreatedAt(Instant.now().minusSeconds(2000));

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(501L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        FlightTask result = service.getTask(501L, authentication);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("timed out");
        verify(eventPublisher).publishTaskStatusChanged(eq(501L), eq(50L), eq("stale"), eq(TaskStatus.FAILED), contains("timed out"));
    }

    @Test
    void getTaskRawKey_andTrajectoryKey_coverMissingAndSuccess() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);
        when(authentication.getPrincipal()).thenReturn(current);

        FlightTask withKeys = new FlightTask();
        withKeys.setId(7L);
        withKeys.setUserId(1L);
        withKeys.setRawLogObjectKey("tasks/1/7/raw/source.bin");
        withKeys.setTrajectoryObjectKey("tasks/1/7/result/trajectory.json");

        when(taskRepository.findById(7L)).thenReturn(Optional.of(withKeys));

        assertThat(service.getTaskRawKey(7L, authentication)).isEqualTo("tasks/1/7/raw/source.bin");
        assertThat(service.getTaskTrajectoryKey(7L, authentication)).isEqualTo("tasks/1/7/result/trajectory.json");

        FlightTask withoutTrajectory = new FlightTask();
        withoutTrajectory.setId(8L);
        withoutTrajectory.setUserId(1L);
        withoutTrajectory.setRawLogObjectKey("");
        when(taskRepository.findById(8L)).thenReturn(Optional.of(withoutTrajectory));

        assertThatThrownBy(() -> service.getTaskRawKey(8L, authentication)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getTaskTrajectoryKey(8L, authentication)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addAiConclusion_returnsImmediatelyWhenAlreadyPresent() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(70L);
        task.setUserId(1L);
        AiConclusion existingConclusion = new AiConclusion();
        existingConclusion.setConclusion("Already generated");
        task.setAiConclusion(existingConclusion);

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(70L)).thenReturn(Optional.of(task));

        FlightTask returned = service.addAiConclusion(70L, authentication);

        assertThat(returned).isSameAs(task);
        verify(fileService, never()).uploadJson(anyString(), anyString());
    }

    @Test
    void addAiConclusion_throwsWhenTrajectoryMissing() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(71L);
        task.setUserId(1L);

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(71L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.addAiConclusion(71L, authentication)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addAiConclusion_handlesInvalidJsonByAppendingPlainTextConclusion() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(72L);
        task.setUserId(1L);
        task.setTrajectoryObjectKey("tasks/1/72/result/trajectory.json");

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(72L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(true);
        when(fileService.getObjectStream("tasks/1/72/result/trajectory.json"))
                .thenReturn(new ByteArrayInputStream("not-json".getBytes(StandardCharsets.UTF_8)));
        when(aiConclusionService.generateConclusion("not-json"))
                .thenReturn(new AiConclusionGenerationResult("text conclusion", AiModel.CUSTOM, null));
        when(aiConclusionRepository.save(any(AiConclusion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(task)).thenReturn(task);

        service.addAiConclusion(72L, authentication);

        verify(fileService).uploadJson(eq("tasks/1/72/result/trajectory.json"), contains("AI Conclusion:"));
    }

    @Test
    void addAiConclusion_wrapsJsonArrayIntoTrajectoryObject() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(74L);
        task.setUserId(1L);
        task.setTrajectoryObjectKey("tasks/1/74/result/trajectory.json");

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(74L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(true);
        when(fileService.getObjectStream("tasks/1/74/result/trajectory.json"))
                .thenReturn(new ByteArrayInputStream("[1,2,3]".getBytes(StandardCharsets.UTF_8)));
        when(aiConclusionService.generateConclusion("[1,2,3]"))
                .thenReturn(new AiConclusionGenerationResult("array conclusion", AiModel.GPT_4O, null));
        when(aiConclusionRepository.save(any(AiConclusion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(task)).thenReturn(task);

        service.addAiConclusion(74L, authentication);

        verify(fileService).uploadJson(eq("tasks/1/74/result/trajectory.json"), contains("\"trajectory\""));
    }

    @Test
    void addAiConclusion_wrapsReadIOExceptionAsBadRequest() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(73L);
        task.setUserId(1L);
        task.setTrajectoryObjectKey("tasks/1/73/result/trajectory.json");

        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken");
            }
        };

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(73L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(true);
        when(fileService.getObjectStream("tasks/1/73/result/trajectory.json")).thenReturn(broken);

        assertThatThrownBy(() -> service.addAiConclusion(73L, authentication)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void addAiConclusion_throwsWhenUnauthenticated() {
        assertThatThrownBy(() -> service.addAiConclusion(1L, null)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void addAiConclusion_throwsWhenRateLimitExceeded() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(75L);
        task.setUserId(1L);
        task.setTrajectoryObjectKey("tasks/1/75/result/trajectory.json");

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(75L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.addAiConclusion(75L, authentication)).isInstanceOf(RateLimitException.class);
    }

    @Test
    void regenerateAiConclusion_overwritesExistingConclusion() {
        User current = new User();
        current.setId(1L);
        current.setRole(Role.USER);

        FlightTask task = new FlightTask();
        task.setId(76L);
        task.setUserId(1L);
        task.setTrajectoryObjectKey("tasks/1/76/result/trajectory.json");

        AiConclusion existing = new AiConclusion();
        existing.setConclusion("old conclusion");
        existing.setAiModel(AiModel.GPT_3_5);
        task.setAiConclusion(existing);

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(76L)).thenReturn(Optional.of(task));
        when(aiConclusionRateLimiter.isAllowed(1L)).thenReturn(true);
        when(fileService.getObjectStream("tasks/1/76/result/trajectory.json"))
                .thenReturn(new ByteArrayInputStream("{\"p\":1}".getBytes(StandardCharsets.UTF_8)));
        when(aiConclusionService.generateConclusion("{\"p\":1}"))
                .thenReturn(new AiConclusionGenerationResult("new conclusion", AiModel.GPT_4O_MINI, null));
        when(aiConclusionRepository.save(any(AiConclusion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(task)).thenReturn(task);

        FlightTask saved = service.regenerateAiConclusion(76L, authentication);

        assertThat(saved.getAiConclusion().getConclusion()).isEqualTo("new conclusion");
        assertThat(saved.getAiConclusion().getAiModel()).isEqualTo(AiModel.GPT_4O_MINI);
    }

    @Test
    void getMyTasks_rejectsNegativeOffsetAndInvalidLimit() {
        assertThatThrownBy(() -> service.getMyTasks(authentication, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getMyTasks(authentication, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getMyTasks(authentication, 0, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void completeTask_throwsWhenTaskMissing() {
        AnalysisResult result = new AnalysisResult();
        result.setTaskId(777L);
        result.setStatus(AnalysisStatus.COMPLETED);
        when(taskRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeTask(result)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void completeTask_successWithoutMetrics_doesNotSaveMetrics() {
        FlightTask task = new FlightTask();
        task.setId(88L);
        task.setUserId(8L);
        task.setStatus(TaskStatus.PROCESSING);
        task.setTitle("no-metrics");

        AnalysisResult result = new AnalysisResult();
        result.setTaskId(88L);
        result.setStatus(AnalysisStatus.COMPLETED);
        result.setTrajectoryObjectKey("tasks/8/88/result/trajectory.json");

        when(taskRepository.findById(88L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        service.completeTask(result);

        verify(metricsRepository, never()).save(any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void deleteTasks_deletesOwnTasksAndSkipsForeign() {
        User current = new User();
        current.setId(5L);
        current.setRole(Role.USER);

        FlightTask own = new FlightTask();
        own.setId(10L);
        own.setUserId(5L);
        own.setRawLogObjectKey("tasks/5/10/raw/source.bin");
        own.setTrajectoryObjectKey("tasks/5/10/result/trajectory.json");
        AiConclusion ownConclusion = new AiConclusion();
        ownConclusion.setFlightTask(own);
        own.setAiConclusion(ownConclusion);

        FlightTask foreign = new FlightTask();
        foreign.setId(11L);
        foreign.setUserId(77L);

        when(authentication.getPrincipal()).thenReturn(current);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(own));
        when(taskRepository.findById(11L)).thenReturn(Optional.of(foreign));

        var result = service.deleteTasks(List.of(10L, 11L, 404L), authentication);

        assertThat(result.getDeletedTaskIds()).containsExactly(10L);
        assertThat(result.getSkippedTaskIds()).containsExactlyInAnyOrder(11L, 404L);
        verify(fileService).delete("tasks/5/10/raw/source.bin");
        verify(fileService).delete("tasks/5/10/result/trajectory.json");
        verify(metricsRepository).deleteByTaskId(10L);
        verify(aiConclusionRepository).delete(ownConclusion);
        verify(taskRepository).delete(own);
    }

    @Test
    void deleteTasks_throwsWhenIdsEmpty() {
        assertThatThrownBy(() -> service.deleteTasks(List.of(), authentication)).isInstanceOf(BadRequestException.class);
    }
}

