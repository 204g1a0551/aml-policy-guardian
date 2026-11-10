package com.bank.aml.repository;

import com.bank.aml.entity.InvestigationTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvestigationTimelineRepository extends JpaRepository<InvestigationTimeline, UUID> {
    List<InvestigationTimeline> findByInvestigationIdOrderByRecordedAtAsc(UUID investigationId);
}
