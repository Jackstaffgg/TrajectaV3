package dev.knalis.trajectaapi.controller.rest.v1;

import dev.knalis.trajectaapi.controller.rest.v1.support.CurrentUserResolver;
import dev.knalis.trajectaapi.dto.task.*;
import dev.knalis.trajectaapi.mapper.task.TaskResponseMapper;
import dev.knalis.trajectaapi.model.task.FlightTask;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.service.impl.cache.FlightTaskDtoCacheService;
import dev.knalis.trajectaapi.service.intrf.task.FileService;
import dev.knalis.trajectaapi.service.intrf.task.FlightTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightTaskControllerTest {

    @Mock
    private FlightTaskService flightTaskService;
    @Mock
    private TaskResponseMapper taskResponseMapper;
    @Mock
    private FlightTaskDtoCacheService flightTaskDtoCacheService;
    @Mock
    private FileService fileService;
    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private Authentication authentication;

    private FlightTaskController controller;

    @BeforeEach
    void setUp() {
        controller = new FlightTaskController(flightTaskService, taskResponseMapper, flightTaskDtoCacheService, fileService, currentUserResolver);
    }

    @Test
    void createTask_returnsCreatedResponse() {
        User user = new User();
        user.setId(1L);

        TaskCreateRequest request = new TaskCreateRequest();
        request.setTitle("A");
        request.setFile(new MockMultipartFile("file", "source.bin", "application/octet-stream", new byte[]{1}));

        FlightTask task = new FlightTask();
        TaskCreateResponse mapped = TaskCreateResponse.builder()
                .id(10L)
                .title("A")
                .status("PROCESSING")
                .build();

        when(currentUserResolver.requireUser(authentication)).thenReturn(user);
        when(flightTaskService.createAndStartAnalysis("A", request.getFile(), 1L, null, java.util.Map.of())).thenReturn(task);
        when(taskResponseMapper.toCreateResponse(task)).thenReturn(mapped);

        var response = controller.createTask(request, authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getData().getId()).isEqualTo(10L);
    }

    @Test
    void getTask_returnsDto() {
        TaskResponse dto = TaskResponse.builder().id(5L).build();

        when(authentication.getName()).thenReturn("alice");
        when(flightTaskDtoCacheService.getTaskDto(5L, "alice", authentication)).thenReturn(dto);

        var response = controller.getTask(5L, authentication);

        assertThat(response.getBody().getData().getId()).isEqualTo(5L);
    }

    @Test
    void downloadRaw_streamsContent() throws Exception {
        when(flightTaskService.getTaskRawKey(1L, authentication)).thenReturn("tasks/1/1/raw/source.bin");
        when(fileService.getObjectStream("tasks/1/1/raw/source.bin"))
                .thenReturn(new ByteArrayInputStream("abc".getBytes()));

        var response = controller.downloadRaw(1L, authentication);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertThat(out.toString()).isEqualTo("abc");
        verify(fileService).getObjectStream("tasks/1/1/raw/source.bin");
    }

    @Test
    void addAiConclusion_returnsMappedTask() {
        FlightTask task = new FlightTask();
        TaskResponse dto = TaskResponse.builder().id(22L).build();

        when(flightTaskService.addAiConclusion(22L, authentication)).thenReturn(task);
        when(taskResponseMapper.toDto(task)).thenReturn(dto);

        var response = controller.addAiConclusion(22L, authentication);

        assertThat(response.getBody().getData().getId()).isEqualTo(22L);
    }

    @Test
    void regenerateAiConclusion_returnsMappedTask() {
        FlightTask task = new FlightTask();
        TaskResponse dto = TaskResponse.builder().id(23L).build();

        when(flightTaskService.regenerateAiConclusion(23L, authentication)).thenReturn(task);
        when(taskResponseMapper.toDto(task)).thenReturn(dto);

        var response = controller.regenerateAiConclusion(23L, authentication);

        assertThat(response.getBody().getData().getId()).isEqualTo(23L);
    }

    @Test
    void deleteTasks_returnsDeleteResult() {
        TaskBulkDeleteRequest request = new TaskBulkDeleteRequest();
        request.setTaskIds(List.of(1L, 2L));

        TaskBulkDeleteResponse result = TaskBulkDeleteResponse.builder()
                .deletedTaskIds(List.of(1L))
                .skippedTaskIds(List.of(2L))
                .build();

        when(flightTaskService.deleteTasks(List.of(1L, 2L), authentication)).thenReturn(result);

        var response = controller.deleteTasks(request, authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getDeletedTaskIds()).containsExactly(1L);
        assertThat(response.getBody().getData().getSkippedTaskIds()).containsExactly(2L);
    }

    @Test
    void getMyTasks_returnsMappedList() {
        TaskResponse dto = TaskResponse.builder().id(7L).build();

        when(authentication.getName()).thenReturn("alice");
        when(flightTaskDtoCacheService.getMyTaskDtos("alice", 0, 20, authentication)).thenReturn(List.of(dto));

        var response = controller.getMyTasks(0, 20, authentication);

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().getFirst().getId()).isEqualTo(7L);
    }

    @Test
    void downloadTrajectory_streamsContent() throws Exception {
        when(flightTaskService.getTaskTrajectoryKey(2L, authentication)).thenReturn("tasks/1/2/result/trajectory.json");
        when(fileService.getObjectStream("tasks/1/2/result/trajectory.json"))
                .thenReturn(new ByteArrayInputStream("xyz".getBytes()));

        var response = controller.downloadTrajectory(2L, authentication);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertThat(out.toString()).isEqualTo("xyz");
        verify(fileService).getObjectStream("tasks/1/2/result/trajectory.json");
    }
}

