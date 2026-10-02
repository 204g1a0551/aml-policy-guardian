package com.bank.aml.util;

import com.bank.aml.exception.FileOversizedException;
import com.bank.aml.exception.InvalidFileException;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Service
public class FileValidatorService {

    private final Tika tika = new Tika();

    private final long maxFileSizeBytes;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/x-tika-ooxml",
        "text/plain",
        "text/x-matlab"
    );

    public FileValidatorService(@Value("${app.storage.max-file-size-bytes:20971520}") long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file cannot be empty.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new FileOversizedException("File size exceeds maximum permitted limit of " + (maxFileSizeBytes / (1024 * 1024)) + " MB.");
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new InvalidFileException("Filename cannot be blank.");
        }

        // Extension check
        String extension = getFileExtension(rawFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException("Unsupported file extension '." + extension + "'. Permitted types: PDF, DOCX, TXT.");
        }

        // MIME validation via content inspection (Tika magic bytes)
        try (InputStream is = file.getInputStream()) {
            String detectedMime = tika.detect(is, rawFilename);
            if (!isMimeAllowed(detectedMime, extension)) {
                throw new InvalidFileException("Content inspection failure: detected MIME type '" + detectedMime + "' does not match extension '." + extension + "'.");
            }
        } catch (IOException e) {
            throw new InvalidFileException("Failed to inspect file MIME type: " + e.getMessage());
        }
    }

    private boolean isMimeAllowed(String detectedMime, String extension) {
        if (ALLOWED_MIME_TYPES.contains(detectedMime)) {
            return true;
        }
        // TXT files can be detected as text/plain, text/csv, etc.
        if ("txt".equalsIgnoreCase(extension) && detectedMime != null && detectedMime.startsWith("text/")) {
            return true;
        }
        return false;
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) {
            return "document.bin";
        }

        // Strip null bytes and directory traversal sequences
        String clean = filename.replace("\0", "").trim();
        clean = clean.replaceAll("[/\\\\.]+[\\/\\\\]", ""); // Remove ../ or ..\
        clean = clean.replaceAll("^[/\\\\]+", "");     // Remove leading slashes
        clean = clean.replaceAll("[^a-zA-Z0-9.\\-_ ]", "_"); // Only allow safe chars

        // Remove dangerous leading dots
        while (clean.startsWith(".")) {
            clean = clean.substring(1);
        }

        if (clean.isBlank()) {
            clean = "document_" + System.currentTimeMillis() + ".bin";
        }

        return clean;
    }

    public String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new InvalidFileException("Failed to compute document SHA-256 checksum: " + e.getMessage());
        }
    }

    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
