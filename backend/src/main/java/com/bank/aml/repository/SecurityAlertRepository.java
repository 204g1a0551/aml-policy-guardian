package com.bank.aml.repository;

import com.bank.aml.entity.SecurityAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, UUID> {
    List<SecurityAlert> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<SecurityAlert> findAllByOrderByCreatedAtDesc();
}
