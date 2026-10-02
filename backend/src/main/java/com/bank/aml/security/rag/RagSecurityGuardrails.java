package com.bank.aml.security.rag;

import java.util.List;
import java.util.regex.Pattern;

public class RagSecurityGuardrails {

    public record ScanResult(
        boolean safe,
        String attackType,
        String reason,
        String sanitizedInput,
        double riskScore
    ) {
        public static ScanResult safe(String sanitized) {
            return new ScanResult(true, null, null, sanitized, 0.0);
        }

        public static ScanResult violation(String attackType, String reason, String input, double riskScore) {
            return new ScanResult(false, attackType, reason, input, riskScore);
        }
    }

    // Direct Prompt Injection & Jailbreak Patterns
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|above|system|policy)\\s+(instructions?|rules?|guidelines?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(you\\s+are\\s+now\\s+in|enable|enter)\\s+(developer|dan|jailbreak|unfiltered|god)\\s+mode", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bypass\\s+(all\\s+)?(safety|security|compliance|aml)\\s+(filters?|policies|rules?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system\\s+override", Pattern.CASE_INSENSITIVE),
        Pattern.compile("act\\s+as\\s+(an?\\s+)?(unrestricted|evil|unfiltered|jailbroken)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+anything\\s+now", Pattern.CASE_INSENSITIVE)
    );

    // System Prompt & Secret Extraction Patterns
    private static final List<Pattern> EXTRACTION_PATTERNS = List.of(
        Pattern.compile("reveal\\s+(the\\s+|your\\s+)?(system\\s+prompt|initial\\s+instructions?|system\\s+instructions?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(print|show|output|repeat|dump)\\s+(the\\s+|your\\s+)?(system\\s+prompt|initial\\s+instructions?|system\\s+instructions?|hidden\\s+prompt)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(what\\s+are\\s+your|tell\\s+me\\s+your)\\s+(system\\s+instructions?|initial\\s+prompts?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(reveal|show|print|output|extract|dump)\\s+(all\\s+)?(secrets?|keys?|api_key|jwt_secret|passwords?|credentials?|database_url)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("return\\s+(all\\s+)?hidden\\s+documents?", Pattern.CASE_INSENSITIVE)
    );

    // XML/Tag Delimiter Breakout Patterns
    private static final List<Pattern> DELIMITER_BREAKOUT_PATTERNS = List.of(
        Pattern.compile("</?(system_instructions|retrieved_compliance_context|compliance_chunk|user_inquiry|system_override)>", Pattern.CASE_INSENSITIVE)
    );

    // Policy Override Directives
    private static final List<Pattern> POLICY_OVERRIDE_PATTERNS = List.of(
        Pattern.compile("ignore\\s+(the\\s+)?(aml|compliance|bsa|fincen)\\s+(polic(y|ies)|procedure|rules?|guidelines?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("approve\\s+(this\\s+)?(wire|transaction|transfer)\\s+(without|bypassing)\\s+(sar|ctr|investigation|edd|cdd)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do\\s+not\\s+file\\s+(a\\s+)?(sar|str|ctr)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Scans an incoming user query for direct prompt injection, exfiltration, or delimiter breakouts.
     */
    public ScanResult scanUserQuery(String query) {
        if (query == null || query.isBlank()) {
            return ScanResult.safe("");
        }

        // 1. Check for Delimiter Breakout Attempts
        for (Pattern p : DELIMITER_BREAKOUT_PATTERNS) {
            if (p.matcher(query).find()) {
                return ScanResult.violation(
                    "DELIMITER_INJECTION",
                    "User input contains reserved XML prompt boundary delimiter tags.",
                    query,
                    0.95
                );
            }
        }

        // 2. Check for Direct Prompt Injection / Jailbreak
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(query).find()) {
                return ScanResult.violation(
                    "DIRECT_PROMPT_INJECTION",
                    "Input matches known jailbreak or instruction override heuristics.",
                    query,
                    0.90
                );
            }
        }

        // 3. Check for System Prompt & Secret Exfiltration
        for (Pattern p : EXTRACTION_PATTERNS) {
            if (p.matcher(query).find()) {
                return ScanResult.violation(
                    "SECRET_EXTRACTION_ATTEMPT",
                    "Input attempts to extract system instructions, confidential configurations, or secrets.",
                    query,
                    0.85
                );
            }
        }

        // 4. Check for Explicit AML Policy Overrides
        for (Pattern p : POLICY_OVERRIDE_PATTERNS) {
            if (p.matcher(query).find()) {
                return ScanResult.violation(
                    "POLICY_OVERRIDE_ATTEMPT",
                    "Input attempts to instruct the assistant to bypass statutory AML compliance policies.",
                    query,
                    0.80
                );
            }
        }

        // Sanitize any remaining XML characters to prevent unexpected delimiter breakouts
        String sanitized = sanitizeXmlDelimiters(query);
        return ScanResult.safe(sanitized);
    }

    /**
     * Sanitizes XML tag markers inside untrusted document chunks before prompt assembly
     * to prevent retrieved chunks from breaking out of `<retrieved_compliance_context>`.
     */
    public static String sanitizeRetrievedChunkText(String chunkText) {
        if (chunkText == null) {
            return "";
        }
        return sanitizeXmlDelimiters(chunkText);
    }

    /**
     * Checks if a document chunk contains adversarial prompt injection keywords.
     */
    public boolean containsIndirectInjection(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return false;
        }
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(chunkText).find()) return true;
        }
        for (Pattern p : EXTRACTION_PATTERNS) {
            if (p.matcher(chunkText).find()) return true;
        }
        for (Pattern p : POLICY_OVERRIDE_PATTERNS) {
            if (p.matcher(chunkText).find()) return true;
        }
        return false;
    }

    private static String sanitizeXmlDelimiters(String input) {
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
