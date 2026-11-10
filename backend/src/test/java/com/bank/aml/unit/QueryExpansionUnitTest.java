package com.bank.aml.unit;

import com.bank.aml.config.RagProperties;
import com.bank.aml.rag.QueryExpansionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryExpansionUnitTest {

    private QueryExpansionService queryExpansionService;
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setMultiQueryEnabled(true);
        queryExpansionService = new QueryExpansionService(ragProperties);
    }

    @Test
    @DisplayName("Query Expansion: Generates structuring and CTR variants for cash smurfing inquiries")
    void testStructuringQueryExpansion() {
        String query = "Customer deposited $9,900 in cash to avoid reporting";
        List<String> variants = queryExpansionService.expandQuery(query);

        assertThat(variants).hasSizeGreaterThanOrEqualTo(2);
        assertThat(variants.get(0)).isEqualTo(query);
        assertThat(variants).anyMatch(v -> v.contains("Currency Transaction Report") && v.contains("structuring"));
    }

    @Test
    @DisplayName("Query Expansion: Generates SAR filing and escalation variants for suspicious activity inquiries")
    void testSarFilingQueryExpansion() {
        String query = "What is the timeline to file a SAR report?";
        List<String> variants = queryExpansionService.expandQuery(query);

        assertThat(variants).hasSizeGreaterThanOrEqualTo(2);
        assertThat(variants).anyMatch(v -> v.contains("Suspicious Activity Report") && v.contains("30 days"));
    }

    @Test
    @DisplayName("Query Expansion: Returns single query when multi-query is disabled")
    void testMultiQueryDisabled() {
        ragProperties.setMultiQueryEnabled(false);
        String query = "Customer deposited $9,900";
        List<String> variants = queryExpansionService.expandQuery(query);

        assertThat(variants).containsExactly(query);
    }
}
