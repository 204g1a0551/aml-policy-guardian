package com.bank.aml.repository;

import com.bank.aml.entity.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    List<IngestionJob> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<IngestionJob> findByIdAndTenantId(UUID id, String tenantId);

    List<IngestionJob> findByTenantIdAndStatus(String tenantId, String status);

    Optional<IngestionJob> findTopByTenantIdAndDocumentIdOrderByCreatedAtDesc(String tenantId, UUID documentId);

    Optional<IngestionJob> findTopByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    @Query("SELECT COUNT(j) FROM IngestionJob j WHERE j.tenantId = :tenantId AND j.status IN ('QUEUED', 'PROCESSING')")
    long countActiveJobsByTenantId(@Param("tenantId") String tenantId);
}
