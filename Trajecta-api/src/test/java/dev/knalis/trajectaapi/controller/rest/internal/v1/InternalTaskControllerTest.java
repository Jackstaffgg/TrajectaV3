package dev.knalis.trajectaapi.controller.rest.internal.v1;

import dev.knalis.trajectaapi.service.intrf.task.FileService;
import dev.knalis.trajectaapi.service.intrf.task.FlightTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalTaskControllerTest {

    @Mock
    private FlightTaskService flightTaskService;
    @Mock
    private FileService fileService;

    private InternalTaskController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalTaskController(flightTaskService, fileService);
    }

    @Test
    void getRawForWorker_returnsStreamForValidToken() throws Exception {
        when(flightTaskService.getTaskRawKeyInternal(2L)).thenReturn("tasks/2/2/raw/source.bin");
        when(fileService.getObjectStream("tasks/2/2/raw/source.bin"))
                .thenReturn(new ByteArrayInputStream("worker".getBytes()));

        var response = controller.getRawForWorker(2L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Objects.requireNonNull(response.getBody()).writeTo(out);

        assertThat(out.toString()).isEqualTo("worker");
    }
}

