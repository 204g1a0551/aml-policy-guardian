package com.bank.aml.repository;

import com.bank.aml.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByStatus(String status);
    List<Document> findByStatusNot(String status);
    List<Document> findByDocumentTypeAndStatusNot(String documentType, String status);
    Optional<Document> findByFilename(String filename);
    Optional<Document> findBySha256ChecksumAndStatusNot(String sha256Checksum, String status);
    boolean existsBySha256ChecksumAndStatusNot(String sha256Checksum, String status);
}
