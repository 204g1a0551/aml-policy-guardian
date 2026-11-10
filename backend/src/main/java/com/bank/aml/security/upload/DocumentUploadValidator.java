package com.bank.aml.security.upload;

import com.bank.aml.exception.FileOversizedException;
import com.bank.aml.exception.InvalidFileException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class DocumentUploadValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadValidator.class);

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "text/plain",
        "text/markdown",
        "application/octet-stream" // Tika may return octet-stream for plain text without extension
    );

    private final long maxFileSizeBytes;
    private final AntivirusScanner antivirusScanner;
    private final Tika tika;

    public DocumentUploadValidator(
        @Value("${app.upload.max-size-bytes:20971520}") long maxFileSizeBytes, // 20 MB default
        AntivirusScanner antivirusScanner
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.antivirusScanner = antivirusScanner;
        this.tika = new Tika();
    }

    public record ValidatedUpload(
        String sanitizedFilename,
        String detectedMimeType,
        byte[] fileBytes
    ) {}

    public ValidatedUpload validateAndScan(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded document file cannot be null or empty.");
        }

        // 1. File Size Verification
        if (file.getSize() > maxFileSizeBytes) {
            throw new FileOversizedException(String.format(
                "Uploaded file size (%d bytes) exceeds maximum permissible threshold (%d bytes).",
                file.getSize(), maxFileSizeBytes));
        }

        // 2. Filename Sanitization & Path Traversal Prevention
        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new InvalidFileException("Document filename is missing.");
        }

        // Extract base filename safely to neutralize directory traversal attempts
        String baseName = rawFilename;
        int lastSlash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }
        baseName = baseName.replace("..", "").replaceAll("^\\.+", "");
        String sanitizedFilename = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (sanitizedFilename.isBlank()) {
            throw new InvalidFileException("Illegal relative or hidden file path in filename: " + rawFilename);
        }

        // 3. Read bytes & Antivirus / Active Content Scanning
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("Failed to read document content for security inspection: " + e.getMessage());
        }

        AntivirusScanner.ScanResult scanResult = antivirusScanner.scan(fileBytes, sanitizedFilename);
        if (!scanResult.clean()) {
            log.error("UPLOAD MALWARE DETECTED: file='{}', threat='{}', details='{}'",
                sanitizedFilename, scanResult.threatName(), scanResult.details());
            throw new InvalidFileException(String.format(
                "Document upload rejected by security antivirus scanner: %s (%s)",
                scanResult.threatName(), scanResult.details()));
        }

        // 4. Verify MIME type via Apache Tika Magic Bytes
        String detectedMime;
        try (InputStream is = file.getInputStream()) {
            detectedMime = tika.detect(is, sanitizedFilename);
        } catch (IOException e) {
            detectedMime = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        }

        if (detectedMime == null || !ALLOWED_MIME_TYPES.contains(detectedMime.toLowerCase())) {
            log.warn("UPLOAD SECURITY REJECTION: Disallowed MIME type '{}' detected for file '{}'", detectedMime, sanitizedFilename);
            throw new InvalidFileException(String.format(
                "Unsupported or disguised file format: detected MIME type '%s'. Only approved PDF, DOCX, and TXT compliance documents are permitted.",
                detectedMime));
        }

        return new ValidatedUpload(sanitizedFilename, detectedMime, fileBytes);
    }
}
