package com.bank.aml;

import com.bank.aml.dto.response.DocumentResponse;
import com.bank.aml.entity.Document;
import com.bank.aml.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private byte[] validPdfBytes;
    private byte[] validDocxBytes;
    private byte[] validTxtBytes;

    @BeforeEach
    void setUp() throws IOException {
        String testSalt = UUID.randomUUID().toString();

        validPdfBytes = createValidPdfBytes("AML Policy Section 1: Customer Identification Program " + testSalt);

        // Generate real, valid DOCX using Apache POI
        try (org.apache.poi.xwpf.usermodel.XWPFDocument docx = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            org.apache.poi.xwpf.usermodel.XWPFParagraph p = docx.createParagraph();
            org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
            r.setText("AML Compliance Standard Operating Procedure Section 2: " + testSalt);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            docx.write(baos);
            validDocxBytes = baos.toByteArray();
        }

        // Valid TXT bytes with unique salt
        validTxtBytes = ("Anti-Money Laundering Customer Identification Program Guidelines " + testSalt + "\nAll customers must provide valid KYC.").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] createValidPdfBytes(String text) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate test PDF: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("1. ADMIN can successfully upload a valid PDF document")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUploadValidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "AML-POL-005_PEP_Screening.pdf",
            "application/pdf",
            validPdfBytes
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Politically Exposed Persons Screening Policy")
                .param("documentType", "POLICY")
                .param("version", "v1.2")
                .param("source", "FIU Compliance Division"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("Politically Exposed Persons Screening Policy"))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.sha256Checksum").isNotEmpty())
            .andReturn();

        DocumentResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), DocumentResponse.class);
        assertThat(documentRepository.findById(response.id())).isPresent();
    }

    @Test
    @DisplayName("2. ADMIN can successfully upload a valid DOCX document")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUploadValidDocx() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "AML-SOP-009_Wire_Escalations.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            validDocxBytes
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Wire Escalations Standard Operating Procedure")
                .param("documentType", "PROCEDURE")
                .param("version", "v2.0")
                .param("source", "Sanctions Office"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Wire Escalations Standard Operating Procedure"))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.sha256Checksum").isNotEmpty());
    }

    @Test
    @DisplayName("3. ADMIN can successfully upload a valid TXT document")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUploadValidTxt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "AML-GUIDE-001_CIP_Checklist.txt",
            "text/plain",
            validTxtBytes
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "CIP Checklist Guidelines")
                .param("documentType", "GUIDELINE")
                .param("version", "v1.0")
                .param("source", "Onboarding Unit"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("CIP Checklist Guidelines"))
            .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    @DisplayName("4. Reject invalid file format / spoofed extension")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testRejectInvalidFileExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "malicious_script.exe",
            "application/x-msdownload",
            new byte[]{0x4D, 0x5A, 0x00, 0x00} // MZ executable header
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Malicious Binary")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AML-DOC-4001"))
            .andExpect(jsonPath("$.title").value("Invalid File Upload"));
    }

    @Test
    @DisplayName("5. Reject oversized file exceeding limit")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testRejectOversizedFile() throws Exception {
        // Create an oversized array (21 MB)
        byte[] oversizedBytes = new byte[21 * 1024 * 1024];

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "oversized_manual.pdf",
            "application/pdf",
            oversizedBytes
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Oversized Policy Manual")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("AML-DOC-4013"))
            .andExpect(jsonPath("$.title").value("File Size Limit Exceeded"));
    }

    @Test
    @DisplayName("6. Reject duplicate document by SHA-256 checksum with 409 Conflict")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testRejectDuplicateDocument() throws Exception {
        byte[] uniqueContent = createValidPdfBytes("Unique Policy " + UUID.randomUUID());

        MockMultipartFile file1 = new MockMultipartFile(
            "file",
            "Unique_Policy.pdf",
            "application/pdf",
            uniqueContent
        );

        // First upload succeeds
        mockMvc.perform(multipart("/api/v1/documents")
                .file(file1)
                .param("title", "Unique Policy Original")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isCreated());

        // Second upload with identical content must be rejected
        MockMultipartFile file2 = new MockMultipartFile(
            "file",
            "Renamed_Copy_Of_Unique_Policy.pdf",
            "application/pdf",
            uniqueContent
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file2)
                .param("title", "Attempted Duplicate Upload")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AML-DOC-4009"))
            .andExpect(jsonPath("$.title").value("Duplicate Document Detected"));
    }

    @Test
    @DisplayName("7. Sanitize malicious path traversal filename")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testSanitizeMaliciousFilename() throws Exception {
        byte[] content = createValidPdfBytes("Traverse Test " + UUID.randomUUID());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../../../../etc/passwd.pdf",
            "application/pdf",
            content
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Directory Traversal Filename Test")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.filename").value(not(containsString(".."))))
            .andExpect(jsonPath("$.filename").value(not(containsString("/"))));
    }

    @Test
    @DisplayName("8. ANALYST cannot upload documents (403 Forbidden)")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testUnauthorizedUploadByAnalyst() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "Unauthorized.pdf",
            "application/pdf",
            validPdfBytes
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Unauthorized Analyst Policy")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AML-SEC-4003"));
    }

    @Test
    @DisplayName("9. ANALYST cannot delete documents (403 Forbidden)")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testUnauthorizedDeleteByAnalyst() throws Exception {
        UUID docId = UUID.fromString("30000000-0000-0000-0000-000000000001"); // Seed doc

        mockMvc.perform(delete("/api/v1/documents/" + docId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AML-SEC-4003"));
    }

    @Test
    @DisplayName("10. Both ADMIN and ANALYST can view documents list (200 OK)")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testGetAllDocumentsByAnalyst() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].title").isNotEmpty());
    }

    @Test
    @DisplayName("11. ADMIN can successfully delete a document (204 No Content)")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDeleteDocumentByAdmin() throws Exception {
        // Upload temporary doc
        byte[] content = createValidPdfBytes("Delete Me " + UUID.randomUUID());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "Doc_To_Delete.pdf",
            "application/pdf",
            content
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Document To Delete")
                .param("documentType", "POLICY")
                .param("version", "v1.0"))
            .andExpect(status().isCreated())
            .andReturn();

        DocumentResponse uploaded = objectMapper.readValue(result.getResponse().getContentAsString(), DocumentResponse.class);

        // Delete document
        mockMvc.perform(delete("/api/v1/documents/" + uploaded.id()))
            .andExpect(status().isNoContent());

        // Verify document is now marked DELETED
        Document deletedDoc = documentRepository.findById(uploaded.id()).orElseThrow();
        assertThat(deletedDoc.getStatus()).isEqualTo("DELETED");

        // Verify GET by ID returns 404 for DELETED document
        mockMvc.perform(get("/api/v1/documents/" + uploaded.id()))
            .andExpect(status().isNotFound());
    }
}
