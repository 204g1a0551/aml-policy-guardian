package com.bank.aml.repository;

import com.bank.aml.entity.AmlInvestigation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AmlInvestigationRepository extends JpaRepository<AmlInvestigation, UUID> {

    Optional<AmlInvestigation> findByIdAndTenantId(UUID id, String tenantId);
    Optional<AmlInvestigation> findByAlertIdAndTenantId(UUID alertId, String tenantId);
    Optional<AmlInvestigation> findByInvestigationReferenceAndTenantId(String investigationReference, String tenantId);

    List<AmlInvestigation> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    @Query("SELECT i FROM AmlInvestigation i WHERE i.tenantId = :tenantId " +
           "AND (CAST(:status AS string) IS NULL OR i.status = :status) " +
           "AND (CAST(:assignedAnalyst AS string) IS NULL OR i.assignedAnalyst = :assignedAnalyst) " +
           "AND (CAST(:query AS string) IS NULL " +
           "     OR LOWER(i.investigationReference) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%') " +
           "     OR LOWER(i.alert.customerName) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%') " +
           "     OR LOWER(i.alert.alertReference) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%') " +
           "     OR LOWER(i.sarNarrativeDraft) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%')) " +
           "ORDER BY i.createdAt DESC")
    Page<AmlInvestigation> searchInvestigations(@Param("tenantId") String tenantId,
                                                @Param("status") String status,
                                                @Param("assignedAnalyst") String assignedAnalyst,
                                                @Param("query") String query,
                                                Pageable pageable);
}
