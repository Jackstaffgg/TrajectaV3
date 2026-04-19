package dev.knalis.trajectaapi.service.intrf.task;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Object storage abstraction used by task and worker flows.
 */
public interface FileService {
    /** Returns configured storage bucket name. */
    String getBucket();

    /** Uploads a binary source file to a specific object key. */
    void uploadToKey(String objectKey, MultipartFile file);

    /** Uploads trajectory JSON payload to a specific object key. */
    void uploadJson(String objectKey, String jsonPayload);

    /** Opens object stream for download. */
    InputStream getObjectStream(String objectKey);

    /** Deletes object by key. */
    void delete(String objectKey);

    /** Copies object content from source key to destination key inside the same bucket. */
    void copy(String sourceObjectKey, String destinationObjectKey);
}


