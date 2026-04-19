package dev.knalis.trajectaapi.service.impl.task;

import dev.knalis.trajectaapi.exception.BadRequestException;
import dev.knalis.trajectaapi.exception.InternalServerException;
import dev.knalis.trajectaapi.service.intrf.task.FileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    
    private static final long MAX_UPLOAD_SIZE_BYTES = 200L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".bin");
    private static final Pattern OBJECT_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9/_\\-.]+$");
    
    private final MinioClient minioClient;
    
    @Getter
    @Value("${minio.bucket}")
    private String bucket;
    
    @Override
    public void uploadToKey(String objectKey, MultipartFile file) {
        validateObjectKey(objectKey);
        validateFileForUpload(file);
        
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(resolveContentTypeForBinaryUpload())
                            .build()
            );
        } catch (Exception e) {
            throw new InternalServerException("Failed to upload file to object storage", e);
        }
    }
    
    @Override
    public void uploadJson(String objectKey, String jsonPayload) {
        validateObjectKey(objectKey);
        if (jsonPayload == null || jsonPayload.isBlank()) {
            throw new BadRequestException("Trajectory JSON must not be empty");
        }
        
        byte[] content = jsonPayload.getBytes(StandardCharsets.UTF_8);
        
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, content.length, -1)
                            .contentType("application/json")
                            .build()
            );
        } catch (Exception e) {
            throw new InternalServerException("Failed to upload trajectory JSON to object storage", e);
        }
    }
    
    @Override
    public InputStream getObjectStream(String objectKey) {
        validateObjectKey(objectKey);
        
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new InternalServerException("Failed to open file stream from object storage", e);
        }
    }
    
    @Override
    public void delete(String objectKey) {
        validateObjectKey(objectKey);
        
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new InternalServerException("Failed to delete file from object storage", e);
        }
    }

    @Override
    public void copy(String sourceObjectKey, String destinationObjectKey) {
        validateObjectKey(sourceObjectKey);
        validateObjectKey(destinationObjectKey);

        try (InputStream sourceStream = getObjectStream(sourceObjectKey)) {
            byte[] payload = sourceStream.readAllBytes();
            try (InputStream destinationStream = new ByteArrayInputStream(payload)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(destinationObjectKey)
                                .stream(destinationStream, payload.length, -1)
                                .contentType(resolveContentTypeForBinaryUpload())
                                .build()
                );
            }
        } catch (Exception e) {
            throw new InternalServerException("Failed to copy file in object storage", e);
        }
    }
    
    private void validateFileForUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be null or empty");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("File name must not be empty");
        }
        
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed limit");
        }
        
        String normalizedFilename = originalFilename.trim().toLowerCase();
        boolean allowedExtension = ALLOWED_EXTENSIONS.stream().anyMatch(normalizedFilename::endsWith);
        if (!allowedExtension) {
            throw new BadRequestException("Only .bin files are allowed");
        }
    }
    
    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException("Object key must not be empty");
        }
        
        if (!OBJECT_KEY_PATTERN.matcher(objectKey).matches()) {
            throw new BadRequestException("Object key contains illegal characters");
        }
        
        if (objectKey.contains("..")) {
            throw new BadRequestException("Object key must not contain path traversal sequences");
        }
        
        if (objectKey.startsWith("/") || objectKey.endsWith("/")) {
            throw new BadRequestException("Object key must not start or end with '/'");
        }
    }
    
    private String resolveContentTypeForBinaryUpload() {
        return "application/octet-stream";
    }
}
