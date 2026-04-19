package dev.knalis.trajectaapi.service.impl;

import dev.knalis.trajectaapi.exception.BadRequestException;
import dev.knalis.trajectaapi.exception.InternalServerException;
import dev.knalis.trajectaapi.service.impl.task.FileServiceImpl;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private MinioClient minioClient;

    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileServiceImpl(minioClient);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @Test
    void uploadToKey_rejectsWrongExtension() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadJson_rejectsBlankPayload() {
        assertThatThrownBy(() -> service.uploadJson("tasks/1/1/result/trajectory.json", "   "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getObjectStream_wrapsMinioExceptions() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("minio fail"));

        assertThatThrownBy(() -> service.getObjectStream("tasks/1/1/raw/source.bin"))
                .isInstanceOf(InternalServerException.class);
    }

    @Test
    void delete_callsMinioRemoveObject() throws Exception {
        service.delete("tasks/1/1/raw/source.bin");
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void uploadToKey_wrapsStorageExceptions() throws Exception {
        var file = new MockMultipartFile("file", "source.bin", "application/octet-stream", new byte[]{1});
        doThrow(new RuntimeException("fail")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", file))
                .isInstanceOf(InternalServerException.class);
    }

    @Test
    void uploadToKey_rejectsIllegalObjectKeys() {
        var file = new MockMultipartFile("file", "source.bin", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> service.uploadToKey("", file)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadToKey("tasks/1/../raw/source.bin", file)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadToKey("/tasks/1/raw/source.bin", file)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadToKey("tasks/1/raw/source.bin/", file)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadToKey("tasks/1/raw/source?.bin", file)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadToKey_rejectsInvalidFileVariants() {
        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", null))
                .isInstanceOf(BadRequestException.class);

        var empty = new MockMultipartFile("file", "source.bin", "application/octet-stream", new byte[]{});
        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", empty))
                .isInstanceOf(BadRequestException.class);

        var blankName = new MockMultipartFile("file", " ", "application/octet-stream", new byte[]{1});
        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", blankName))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadJson_wrapsStorageExceptions() throws Exception {
        doThrow(new RuntimeException("fail")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> service.uploadJson("tasks/1/1/result/trajectory.json", "{\"a\":1}"))
                .isInstanceOf(InternalServerException.class);
    }

    @Test
    void getAndDelete_rejectInvalidObjectKey() {
        assertThatThrownBy(() -> service.getObjectStream("../x")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.delete("../x")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void delete_wrapsStorageExceptions() throws Exception {
        doThrow(new RuntimeException("fail")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> service.delete("tasks/1/1/raw/source.bin"))
                .isInstanceOf(InternalServerException.class);
    }

    @Test
    void uploadJson_rejectsInvalidObjectKey() {
        assertThatThrownBy(() -> service.uploadJson("../bad", "{\"a\":1}"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadToKey_rejectsNullFilename() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);

        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void uploadToKey_rejectsTooLargeFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("source.bin");
        when(file.getSize()).thenReturn(5001L * 1024L * 1024L);

        assertThatThrownBy(() -> service.uploadToKey("tasks/1/1/raw/source.bin", file))
                .isInstanceOf(BadRequestException.class);
    }
}

