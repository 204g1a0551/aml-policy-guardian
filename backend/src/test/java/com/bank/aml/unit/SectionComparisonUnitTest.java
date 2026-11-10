package com.bank.aml.unit;

import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.PolicySectionDiff;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.service.SectionComparisonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SectionComparisonUnitTest {

    private DocumentChunkRepository chunkRepository;
    private SectionComparisonService comparisonService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        chunkRepository = mock(DocumentChunkRepository.class);
        comparisonService = new SectionComparisonService(chunkRepository, objectMapper);
    }

    @Test
    @DisplayName("Diff Engine: Identifies addition of new regulatory section")
    void testSectionAdditionDetected() {
        Document baseDoc = createDoc("v1.0", "AML-SOP-001");
        Document targetDoc = createDoc("v2.0", "AML-SOP-001");

        DocumentChunk baseChunk = createChunk(baseDoc.getId(), "1.0 Purpose", "This is the initial purpose.");
        DocumentChunk targetChunk1 = createChunk(targetDoc.getId(), "1.0 Purpose", "This is the initial purpose.");
        DocumentChunk targetChunk2 = createChunk(targetDoc.getId(), "2.0 New EDD Requirements", "Enhanced Due Diligence is mandatory for high-risk accounts exceeding $10,000 within 24 hours.");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(baseDoc.getId())).thenReturn(List.of(baseChunk));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId())).thenReturn(List.of(targetChunk1, targetChunk2));

        List<PolicySectionDiff> diffs = comparisonService.comparePolicyDocuments(baseDoc, targetDoc);

        assertThat(diffs).hasSize(2);
        PolicySectionDiff addedSection = diffs.stream().filter(d -> d.getSectionTitle().contains("2.0")).findFirst().orElseThrow();
        assertThat(addedSection.getChangeType()).isEqualTo("ADDITION");
        assertThat(addedSection.getRegulatoryImpactSummary()).contains("NEW REQUIREMENT");
        assertThat(addedSection.getRegulatoryImpactSummary()).contains("$10,000");
    }

    @Test
    @DisplayName("Diff Engine: Identifies rescinded / deleted section")
    void testSectionDeletionDetected() {
        Document baseDoc = createDoc("v1.0", "AML-SOP-001");
        Document targetDoc = createDoc("v2.0", "AML-SOP-001");

        DocumentChunk baseChunk1 = createChunk(baseDoc.getId(), "1.0 Scope", "Scope guidance.");
        DocumentChunk baseChunk2 = createChunk(baseDoc.getId(), "3.0 Legacy Method", "Legacy manual reporting for SAR.");
        DocumentChunk targetChunk = createChunk(targetDoc.getId(), "1.0 Scope", "Scope guidance.");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(baseDoc.getId())).thenReturn(List.of(baseChunk1, baseChunk2));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId())).thenReturn(List.of(targetChunk));

        List<PolicySectionDiff> diffs = comparisonService.comparePolicyDocuments(baseDoc, targetDoc);

        PolicySectionDiff deletedSection = diffs.stream().filter(d -> d.getSectionTitle().contains("3.0")).findFirst().orElseThrow();
        assertThat(deletedSection.getChangeType()).isEqualTo("DELETION");
        assertThat(deletedSection.getRegulatoryImpactSummary()).contains("RESCINDED REQUIREMENT");
    }

    @Test
    @DisplayName("Diff Engine: Detects statutory threshold and filing deadline modifications")
    void testModifiedObligationDetected() {
        Document baseDoc = createDoc("v1.0", "AML-SOP-001");
        Document targetDoc = createDoc("v2.0", "AML-SOP-001");

        DocumentChunk baseChunk = createChunk(
            baseDoc.getId(), "4.0 SAR Filing",
            "An investigator shall file a SAR for suspicious transactions exceeding $10,000 within 30 days of detection."
        );
        DocumentChunk targetChunk = createChunk(
            targetDoc.getId(), "4.0 SAR Filing",
            "An investigator must file a SAR for suspicious transactions exceeding $5,000 within 15 calendar days of detection."
        );

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(baseDoc.getId())).thenReturn(List.of(baseChunk));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId())).thenReturn(List.of(targetChunk));

        List<PolicySectionDiff> diffs = comparisonService.comparePolicyDocuments(baseDoc, targetDoc);

        assertThat(diffs).hasSize(1);
        PolicySectionDiff modDiff = diffs.get(0);
        assertThat(modDiff.getChangeType()).isEqualTo("MODIFIED_OBLIGATION");
        assertThat(modDiff.getRegulatoryImpactSummary()).contains("Monetary threshold shifted");
        assertThat(modDiff.getRegulatoryImpactSummary()).contains("Compliance timeline modified");
    }

    @Test
    @DisplayName("Diff Engine: Correctly classifies identical sections as UNCHANGED")
    void testUnchangedSectionDetected() {
        Document baseDoc = createDoc("v1.0", "AML-SOP-001");
        Document targetDoc = createDoc("v2.0", "AML-SOP-001");

        DocumentChunk baseChunk = createChunk(baseDoc.getId(), "1.0 Governance", "Standard regulatory oversight.");
        DocumentChunk targetChunk = createChunk(targetDoc.getId(), "1.0 Governance", "Standard regulatory oversight.");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(baseDoc.getId())).thenReturn(List.of(baseChunk));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId())).thenReturn(List.of(targetChunk));

        List<PolicySectionDiff> diffs = comparisonService.comparePolicyDocuments(baseDoc, targetDoc);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).getChangeType()).isEqualTo("UNCHANGED");
        assertThat(diffs.get(0).getRegulatoryImpactSummary()).contains("No regulatory changes detected");
    }

    private Document createDoc(String version, String code) {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setTitle("Transaction Monitoring Standard");
        doc.setVersion(version);
        doc.setPolicyCode(code);
        doc.setTenantId("default-bank");
        return doc;
    }

    private DocumentChunk createChunk(UUID docId, String section, String text) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocumentId(docId);
        chunk.setSection(section);
        chunk.setChunkText(text);
        chunk.setChunkIndex(0);
        return chunk;
    }
}
