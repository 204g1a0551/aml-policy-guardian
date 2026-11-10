package com.bank.aml.repository;

import com.bank.aml.entity.AmlTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AmlTransactionRepository extends JpaRepository<AmlTransaction, UUID> {
    List<AmlTransaction> findByAlertIdOrderByTransactionDateDesc(UUID alertId);
}
