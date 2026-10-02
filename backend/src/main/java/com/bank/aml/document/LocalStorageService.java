package com.bank.aml.document;

import com.bank.aml.exception.DocumentProcessingException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalStorageService implements DocumentStorageService {

    private final Path rootLocation;

    public LocalStorageService(@Value("${app.storage.local-dir:./storage/documents}") String storageDir) {
        this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new DocumentProcessingException("Could not initialize document storage directory: " + rootLocation, e);
        }
    }

    @Override
    public String store(InputStream inputStream, String sanitizedFilename, String mimeType) {
        try {
            String uniquePrefix = UUID.randomUUID().toString();
            String storedFilename = uniquePrefix + "_" + sanitizedFilename;
            Path destinationFile = this.rootLocation.resolve(storedFilename).normalize();

            // Strict path traversal defense
            if (!destinationFile.getParent().equals(this.rootLocation)) {
                throw new DocumentProcessingException("Security violation: cannot store file outside current storage directory.");
            }

            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return storedFilename;
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to store file: " + sanitizedFilename, e);
        }
    }

    @Override
    public Resource loadAsResource(String storagePath) {
        try {
            Path file = rootLocation.resolve(storagePath).normalize();
            if (!file.startsWith(rootLocation)) {
                throw new DocumentProcessingException("Security violation: path traversal detected on file retrieval.");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new DocumentProcessingException("Could not read file: " + storagePath);
            }
        } catch (MalformedURLException e) {
            throw new DocumentProcessingException("Could not read file from malformed URL: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Path file = rootLocation.resolve(storagePath).normalize();
            if (file.startsWith(rootLocation)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            // Log and continue
        }
    }

    @Override
    public boolean exists(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }
        Path file = rootLocation.resolve(storagePath).normalize();
        return file.startsWith(rootLocation) && Files.exists(file);
    }
}
