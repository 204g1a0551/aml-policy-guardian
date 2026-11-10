package com.bank.aml.unit;

import com.bank.aml.exception.InvalidFileException;
import com.bank.aml.security.upload.AntivirusScanner;
import com.bank.aml.security.upload.DefaultAntivirusScanner;
import com.bank.aml.security.upload.DocumentUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidationUnitTest {

    private DocumentUploadValidator validator;
    private AntivirusScanner antivirusScanner;

    @BeforeEach
    void setUp() {
        antivirusScanner = new DefaultAntivirusScanner();
        validator = new DocumentUploadValidator(10 * 1024 * 1024, antivirusScanner); // 10MB limit
    }

    @Test
    @DisplayName("Upload: Valid PDF policy document passes validation and scanning")
    void testValidPdfUpload() {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile file = new MockMultipartFile(
            "file", "AML-SOP-001.pdf", "application/pdf", pdfBytes
        );

        DocumentUploadValidator.ValidatedUpload result = validator.validateAndScan(file);

        assertThat(result.sanitizedFilename()).isEqualTo("AML-SOP-001.pdf");
        assertThat(result.detectedMimeType()).isEqualTo("application/pdf");
        assertThat(result.fileBytes()).isEqualTo(pdfBytes);
    }

    @Test
    @DisplayName("Upload: EICAR standard antivirus test signature is blocked immediately")
    void testEicarAntivirusRejection() {
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        MockMultipartFile file = new MockMultipartFile(
            "file", "infected_policy.txt", "text/plain", eicar.getBytes(StandardCharsets.ISO_8859_1)
        );

        assertThatThrownBy(() -> validator.validateAndScan(file))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("EICAR_TEST_VIRUS");
    }

    @Test
    @DisplayName("Upload: Disguised PE binary (MZ header) disguised as .pdf is rejected")
    void testDisguisedPeBinaryRejection() {
        byte[] fakePdfBytes = new byte[] { 'M', 'Z', 0x00, 0x01, 0x02, 0x03 }; // MZ header
        MockMultipartFile file = new MockMultipartFile(
            "file", "malicious.pdf", "application/pdf", fakePdfBytes
        );

        assertThatThrownBy(() -> validator.validateAndScan(file))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("DISGUISED_PE_BINARY");
    }

    @Test
    @DisplayName("Upload: Path traversal attempts in filename are rejected")
    void testPathTraversalFilenameRejection() {
        byte[] content = "Policy content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "../../../..", "text/plain", content
        );

        assertThatThrownBy(() -> validator.validateAndScan(file))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("Illegal relative or hidden file path");
    }

    @Test
    @DisplayName("Upload: Oversized file exceeding byte limit is rejected")
    void testOversizedFileRejection() {
        DocumentUploadValidator smallValidator = new DocumentUploadValidator(100, antivirusScanner); // 100 bytes max
        byte[] largeBytes = new byte[200];
        MockMultipartFile file = new MockMultipartFile(
            "file", "big.txt", "text/plain", largeBytes
        );

        assertThatThrownBy(() -> smallValidator.validateAndScan(file))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("exceeds maximum permissible threshold");
    }
}
