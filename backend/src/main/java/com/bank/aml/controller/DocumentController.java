package com.bank.aml.controller;

import com.bank.aml.dto.request.DocumentUploadRequest;
import com.bank.aml.dto.response.DocumentResponse;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> uploadDocument(
        @RequestPart("file") MultipartFile file,
        @RequestParam("title") String title,
        @RequestParam("documentType") String documentType,
        @RequestParam("version") String version,
        @RequestParam(value = "source", required = false) String source,
        @AuthenticationPrincipal SecurityUserPrincipal principal
    ) {
        DocumentUploadRequest request = new DocumentUploadRequest(title, documentType, version, source);
        UUID uploaderId = principal != null ? principal.getId() : null;

        DocumentResponse response = documentService.uploadDocument(file, request, uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<DocumentResponse>> getAllDocuments(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "documentType", required = false) String documentType
    ) {
        List<DocumentResponse> documents = documentService.getAllDocuments(status, documentType);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable UUID id) {
        DocumentResponse document = documentService.getDocumentById(id);
        return ResponseEntity.ok(document);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
