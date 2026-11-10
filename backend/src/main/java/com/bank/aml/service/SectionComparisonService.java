package com.bank.aml.service;

import com.bank.aml.entity.Document;
import com.bank.aml.entity.DocumentChunk;
import com.bank.aml.entity.PolicySectionDiff;
import com.bank.aml.repository.DocumentChunkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SectionComparisonService {

    private static final Logger log = LoggerFactory.getLogger(SectionComparisonService.class);

    private static final Pattern MONETARY_PATTERN = Pattern.compile("(\\$[0-9,]+(?:\\.[0-9]{2})?|[0-9,]+(?:\\.[0-9]{2})?\\s*(?:USD|EUR|GBP))", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEADLINE_PATTERN = Pattern.compile("\\b(?:within|in)\\s+(\\d+)\\s+(?:calendar\\s+|business\\s+)?(?:days?|hours?|months?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBLIGATION_MODAL_PATTERN = Pattern.compile("\\b(shall|must|mandatory|required to|prohibited from|strictly forbidden)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGULATORY_TOPIC_PATTERN = Pattern.compile("\\b(SAR|CTR|PEP|CDD|EDD|CIP|FinCEN|OFAC|FATF|Beneficial Owner(?:ship)?)\\b", Pattern.CASE_INSENSITIVE);

    private final DocumentChunkRepository documentChunkRepository;
    private final ObjectMapper objectMapper;

    public SectionComparisonService(DocumentChunkRepository documentChunkRepository, ObjectMapper objectMapper) {
        this.documentChunkRepository = documentChunkRepository;
        this.objectMapper = objectMapper;
    }

    public List<PolicySectionDiff> comparePolicyDocuments(Document baseDoc, Document targetDoc) {
        log.info("Comparing policy versions: base='{}' (v{}) vs target='{}' (v{}) for policyCode='{}'",
            baseDoc.getTitle(), baseDoc.getVersion(), targetDoc.getTitle(), targetDoc.getVersion(), targetDoc.getPolicyCode());

        List<DocumentChunk> baseChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(baseDoc.getId());
        List<DocumentChunk> targetChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(targetDoc.getId());

        // Group chunks by normalized section title
        Map<String, List<DocumentChunk>> baseSectionMap = groupChunksBySection(baseChunks);
        Map<String, List<DocumentChunk>> targetSectionMap = groupChunksBySection(targetChunks);

        // Ordered union of section titles
        LinkedHashSet<String> allSections = new LinkedHashSet<>();
        allSections.addAll(baseSectionMap.keySet());
        allSections.addAll(targetSectionMap.keySet());

        List<PolicySectionDiff> diffList = new ArrayList<>();
        String tenantId = targetDoc.getTenantId() != null ? targetDoc.getTenantId() : "default-bank";
        String policyCode = targetDoc.getPolicyCode() != null ? targetDoc.getPolicyCode() : baseDoc.getPolicyCode();

        for (String sectionTitle : allSections) {
            List<DocumentChunk> baseList = baseSectionMap.get(sectionTitle);
            List<DocumentChunk> targetList = targetSectionMap.get(sectionTitle);

            String baseText = baseList != null ? mergeChunkTexts(baseList) : null;
            String targetText = targetList != null ? mergeChunkTexts(targetList) : null;

            List<String> affectedChunkIds = targetList != null
                ? targetList.stream().map(c -> c.getId().toString()).collect(Collectors.toList())
                : Collections.emptyList();

            String affectedChunksJson = "[]";
            try {
                affectedChunksJson = objectMapper.writeValueAsString(affectedChunkIds);
            } catch (Exception ignored) {}

            String changeType;
            String impactSummary = "";

            if (baseText == null && targetText != null) {
                changeType = "ADDITION";
                impactSummary = analyzeAddition(targetText);
            } else if (baseText != null && targetText == null) {
                changeType = "DELETION";
                impactSummary = analyzeDeletion(baseText);
            } else if (normalizeWhitespace(baseText).equalsIgnoreCase(normalizeWhitespace(targetText))) {
                changeType = "UNCHANGED";
                impactSummary = "No regulatory changes detected in this section.";
            } else {
                changeType = "MODIFIED_OBLIGATION";
                impactSummary = analyzeModification(baseText, targetText);
            }

            PolicySectionDiff diff = new PolicySectionDiff(
                tenantId,
                policyCode,
                baseDoc.getId(),
                targetDoc.getId(),
                sectionTitle,
                changeType,
                baseText,
                targetText,
                impactSummary,
                affectedChunksJson
            );

            diffList.add(diff);
        }

        return diffList;
    }

    private Map<String, List<DocumentChunk>> groupChunksBySection(List<DocumentChunk> chunks) {
        Map<String, List<DocumentChunk>> map = new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            String section = chunk.getSection();
            if (section == null || section.isBlank()) {
                section = "Section " + (chunk.getChunkIndex() != null ? (chunk.getChunkIndex() + 1) : "General");
            }
            map.computeIfAbsent(section.trim(), k -> new ArrayList<>()).add(chunk);
        }
        return map;
    }

    private String mergeChunkTexts(List<DocumentChunk> chunks) {
        return chunks.stream()
            .sorted(Comparator.comparing(c -> c.getChunkIndex() != null ? c.getChunkIndex() : 0))
            .map(DocumentChunk::getChunkText)
            .collect(Collectors.joining("\n\n"));
    }

    private String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private String analyzeAddition(String newContent) {
        List<String> highlights = new ArrayList<>();
        highlights.add("[NEW REQUIREMENT]");

        Set<String> topics = extractMatches(newContent, REGULATORY_TOPIC_PATTERN);
        if (!topics.isEmpty()) {
            highlights.add("Introduces new provisions concerning: " + String.join(", ", topics));
        }

        Set<String> amounts = extractMatches(newContent, MONETARY_PATTERN);
        if (!amounts.isEmpty()) {
            highlights.add("Establishes statutory threshold(s): " + String.join(", ", amounts));
        }

        Set<String> deadlines = extractMatches(newContent, DEADLINE_PATTERN);
        if (!deadlines.isEmpty()) {
            highlights.add("Establishes compliance timeframe(s): " + String.join(", ", deadlines));
        }

        return String.join(". ", highlights) + ".";
    }

    private String analyzeDeletion(String oldContent) {
        List<String> highlights = new ArrayList<>();
        highlights.add("[RESCINDED REQUIREMENT]");

        Set<String> topics = extractMatches(oldContent, REGULATORY_TOPIC_PATTERN);
        if (!topics.isEmpty()) {
            highlights.add("Removes prior obligations concerning: " + String.join(", ", topics));
        }

        return String.join(". ", highlights) + ".";
    }

    private String analyzeModification(String oldContent, String newContent) {
        List<String> changes = new ArrayList<>();

        // 1. Monetary threshold shift
        Set<String> oldAmounts = extractMatches(oldContent, MONETARY_PATTERN);
        Set<String> newAmounts = extractMatches(newContent, MONETARY_PATTERN);
        if (!oldAmounts.equals(newAmounts) && (!oldAmounts.isEmpty() || !newAmounts.isEmpty())) {
            changes.add(String.format("Monetary threshold shifted from %s to %s",
                oldAmounts.isEmpty() ? "none" : oldAmounts,
                newAmounts.isEmpty() ? "none" : newAmounts));
        }

        // 2. Deadline shift
        Set<String> oldDeadlines = extractMatches(oldContent, DEADLINE_PATTERN);
        Set<String> newDeadlines = extractMatches(newContent, DEADLINE_PATTERN);
        if (!oldDeadlines.equals(newDeadlines) && (!oldDeadlines.isEmpty() || !newDeadlines.isEmpty())) {
            changes.add(String.format("Compliance timeline modified from %s to %s",
                oldDeadlines.isEmpty() ? "unspecified" : oldDeadlines,
                newDeadlines.isEmpty() ? "unspecified" : newDeadlines));
        }

        // 3. Modal strength change
        Set<String> oldModals = extractMatches(oldContent, OBLIGATION_MODAL_PATTERN);
        Set<String> newModals = extractMatches(newContent, OBLIGATION_MODAL_PATTERN);
        if (!oldModals.equals(newModals)) {
            changes.add(String.format("Obligation modal verb strength updated (%s -> %s)", oldModals, newModals));
        }

        // 4. Regulatory topics referenced
        Set<String> newTopics = extractMatches(newContent, REGULATORY_TOPIC_PATTERN);
        if (!newTopics.isEmpty()) {
            changes.add("Impacts regulatory domain(s): " + String.join(", ", newTopics));
        }

        if (changes.isEmpty()) {
            return "[TEXTUAL REVISION] Wording, formatting, or guidance clarification updated without altered statutory thresholds.";
        }

        return "[OBLIGATION MODIFIED] " + String.join("; ", changes) + ".";
    }

    private Set<String> extractMatches(String text, Pattern pattern) {
        if (text == null) return Collections.emptySet();
        Set<String> matches = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(0).trim());
        }
        return matches;
    }
}
