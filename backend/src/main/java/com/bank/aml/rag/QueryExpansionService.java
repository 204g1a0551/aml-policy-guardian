package com.bank.aml.rag;

import com.bank.aml.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Query Expansion & Multi-Query Generation Service for AML Regulatory Inquiries.
 * Generates domain-aware query reformulations to bridge compliance vocabulary gaps
 * (e.g. mapping "smurfing" to "structuring" and "CTR reporting", or "PEP" to "Enhanced Due Diligence").
 */
@Service
public class QueryExpansionService {

    private final RagProperties ragProperties;

    public QueryExpansionService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<String> expandQuery(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return Collections.emptyList();
        }

        List<String> queries = new ArrayList<>();
        String trimmed = originalQuery.trim();
        queries.add(trimmed);

        if (!ragProperties.isMultiQueryEnabled()) {
            return queries;
        }

        String lower = trimmed.toLowerCase();

        // 1. Structuring / Smurfing / CTR Expansion
        if (lower.contains("structur") || lower.contains("smurf") || lower.contains("9,900") || lower.contains("9,500") || lower.contains("9900") || lower.contains("10,000") || lower.contains("cash deposit")) {
            queries.add("Currency Transaction Report CTR threshold structuring smurfing cash deposits FinCEN Form 111 TM-RULE-101");
        }

        // 2. High-Value Alert & Monitoring Escalation
        if (lower.contains("high-value") || lower.contains("high value") || lower.contains("alrt_hvt") || lower.contains("large transaction")) {
            queries.add("High-Value Transaction Monitoring and Investigation Procedure ALRT_HVT_01 escalation workflow steps");
        }

        // 3. SAR Filing / Suspicious Activity Timelines
        if (lower.contains("sar") || lower.contains("suspicious activity") || lower.contains("file a report") || lower.contains("timeline")) {
            queries.add("Suspicious Activity Report SAR mandatory filing timeframe 30 days FinCEN Form 111 escalation procedure");
        }

        // 4. PEP / Enhanced Due Diligence (EDD)
        if (lower.contains("pep") || lower.contains("politically exposed") || lower.contains("edd") || lower.contains("due diligence")) {
            queries.add("Customer Due Diligence CDD Enhanced Due Diligence EDD Politically Exposed Persons PEP screening requirements");
        }

        // Return up to 3 queries (original + top 2 variants)
        return queries.stream().distinct().limit(3).toList();
    }
}
