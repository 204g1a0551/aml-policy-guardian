package com.bank.aml.repository;

import com.bank.aml.entity.AmlAlert;
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
public interface AmlAlertRepository extends JpaRepository<AmlAlert, UUID> {

    Optional<AmlAlert> findByTenantIdAndAlertReference(String tenantId, String alertReference);
    Optional<AmlAlert> findByIdAndTenantId(UUID id, String tenantId);

    List<AmlAlert> findByTenantIdOrderByTriggeredAtDesc(String tenantId);
    List<AmlAlert> findByTenantIdAndStatusOrderByTriggeredAtDesc(String tenantId, String status);

    @Query("SELECT a FROM AmlAlert a WHERE a.tenantId = :tenantId " +
           "AND (CAST(:status AS string) IS NULL OR a.status = :status) " +
           "AND (CAST(:severity AS string) IS NULL OR a.severity = :severity) " +
           "AND (CAST(:scenarioCode AS string) IS NULL OR a.scenarioCode = :scenarioCode) " +
           "AND (CAST(:query AS string) IS NULL " +
           "     OR LOWER(a.alertReference) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%') " +
           "     OR LOWER(a.customerName) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%') " +
           "     OR LOWER(a.scenarioTitle) LIKE CONCAT('%', LOWER(CAST(:query AS string)), '%')) " +
           "ORDER BY a.triggeredAt DESC")
    Page<AmlAlert> searchAlerts(@Param("tenantId") String tenantId,
                                @Param("status") String status,
                                @Param("severity") String severity,
                                @Param("scenarioCode") String scenarioCode,
                                @Param("query") String query,
                                Pageable pageable);
}
