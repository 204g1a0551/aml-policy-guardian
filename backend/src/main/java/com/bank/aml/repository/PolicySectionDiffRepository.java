package com.bank.aml.repository;

import com.bank.aml.entity.PolicySectionDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicySectionDiffRepository extends JpaRepository<PolicySectionDiff, UUID> {

    List<PolicySectionDiff> findByTenantIdAndPolicyCodeOrderByCreatedAtDesc(String tenantId, String policyCode);

    List<PolicySectionDiff> findByTenantIdAndBaseDocumentIdAndTargetDocumentId(String tenantId, UUID baseDocumentId, UUID targetDocumentId);

    List<PolicySectionDiff> findByTenantIdAndHumanValidationStatus(String tenantId, String status);
}
