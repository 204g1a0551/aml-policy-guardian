package com.bank.aml.document;

import org.springframework.core.io.Resource;
import java.io.InputStream;

/**
 * Storage abstraction interface for document binaries.
 * Allows transparent swapping between Local Filesystem storage and AWS S3 object store.
 */
public interface DocumentStorageService {

    /**
     * Stores an input stream in the underlying storage provider.
     *
     * @param inputStream raw file input stream
     * @param sanitizedFilename clean filename without path traversal characters
     * @param mimeType detected file MIME type
     * @return unique relative or absolute storage path / S3 key
     */
    String store(InputStream inputStream, String sanitizedFilename, String mimeType);

    /**
     * Loads the stored document as a Spring Resource for retrieval or processing.
     *
     * @param storagePath the path or S3 key returned by store()
     * @return Spring Resource
     */
    Resource loadAsResource(String storagePath);

    /**
     * Deletes the stored document binary.
     *
     * @param storagePath the path or S3 key
     */
    void delete(String storagePath);

    /**
     * Checks if the resource exists in storage.
     *
     * @param storagePath the path or S3 key
     * @return true if exists
     */
    boolean exists(String storagePath);
}
