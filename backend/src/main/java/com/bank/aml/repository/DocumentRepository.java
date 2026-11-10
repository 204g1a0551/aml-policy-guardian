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

    List<Document> findByTenantIdAndStatusNot(String tenantId, String status);
    List<Document> findByTenantIdAndStatus(String tenantId, String status);
    List<Document> findByTenantIdAndDocumentTypeAndStatusNot(String tenantId, String documentType, String status);
    boolean existsByTenantIdAndSha256ChecksumAndStatusNot(String tenantId, String sha256Checksum, String status);

    List<Document> findByTenantIdAndPolicyCodeOrderByCreatedAtDesc(String tenantId, String policyCode);
    Optional<Document> findByTenantIdAndPolicyCodeAndIsEffectiveTrue(String tenantId, String policyCode);
    List<Document> findByTenantIdAndIsEffectiveTrue(String tenantId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT d.policyCode FROM Document d WHERE d.tenantId = :tenantId AND d.policyCode IS NOT NULL ORDER BY d.policyCode ASC")
    List<String> findDistinctPolicyCodesByTenantId(@org.springframework.data.repository.query.Param("tenantId") String tenantId);
}
