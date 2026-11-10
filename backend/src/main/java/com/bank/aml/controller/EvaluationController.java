package com.bank.aml.controller;

import com.bank.aml.dto.response.*;
import com.bank.aml.service.EvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/evaluation")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<EvaluationMetricsResponse> getEvaluationMetrics() {
        return ResponseEntity.ok(evaluationService.getEvaluationMetrics());
    }

    @GetMapping("/history")
    public ResponseEntity<List<BenchmarkRunResponse>> getBenchmarkHistory() {
        return ResponseEntity.ok(evaluationService.getBenchmarkHistory());
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<RegressionAlertResponse>> getRegressionAlerts() {
        return ResponseEntity.ok(evaluationService.getActiveAlerts());
    }

    @GetMapping("/compare")
    public ResponseEntity<BenchmarkComparisonResponse> compareBenchmarkRuns(
        @RequestParam(defaultValue = "baseline") String baselineId,
        @RequestParam(defaultValue = "candidate") String candidateId
    ) {
        return ResponseEntity.ok(evaluationService.compareRuns(baselineId, candidateId));
    }
}
