package com.bank.aml.security.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class RagOutputValidator {

    private static final Logger log = LoggerFactory.getLogger(RagOutputValidator.class);

    public record OutputValidationResult(
        boolean valid,
        String validatedContent,
        String violationType,
        String violationDetail
    ) {}

    private static final List<Pattern> SECRET_LEAK_PATTERNS = List.of(
        Pattern.compile("ey[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*"), // JWT token pattern
        Pattern.compile("sk-[A-Za-z0-9]{20,}"), // OpenAI API key pattern
        Pattern.compile("jdbc:postgresql://[^\\s]+"), // Database connection string
        Pattern.compile("\\$2[aby]\\$\\d{2}\\$[A-Za-z0-9./]{53}") // BCrypt hash
    );

    private static final List<Pattern> SYSTEM_LEAK_PATTERNS = List.of(
        Pattern.compile("<system_instructions>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("CONSTITUTIONAL DIRECTIVES AND INSTRUCTION HIERARCHY", Pattern.CASE_INSENSITIVE),
        Pattern.compile(InstructionHierarchyPromptBuilder.CANARY_TOKEN, Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> ADVERSARIAL_COMPLIANCE_PATTERNS = List.of(
        Pattern.compile("I (have|will) ignore(d)? (the|all) (previous )?instructions?", Pattern.CASE_INSENSITIVE),
        Pattern.compile("SAR (has been )?waived without investigation", Pattern.CASE_INSENSITIVE),
        Pattern.compile("wire transfer (is )?approved without (SAR|CTR|investigation)", Pattern.CASE_INSENSITIVE)
    );

    private static final String SAFE_REFUSAL_MESSAGE =
        "I am an authorized AML Compliance Assistant. I can only provide guidance grounded in approved bank compliance policies. I cannot execute instructions that waive statutory AML requirements, override reporting thresholds, or disclose internal system parameters.";

    /**
     * Inspects generated LLM output for confidential data leakage or policy compromise before returning to the user.
     */
    public OutputValidationResult validateOutput(String generatedOutput) {
        if (generatedOutput == null || generatedOutput.isBlank()) {
            return new OutputValidationResult(true, "", null, null);
        }

        // 1. Check for canary token and system instruction leakage
        for (Pattern p : SYSTEM_LEAK_PATTERNS) {
            if (p.matcher(generatedOutput).find()) {
                log.error("SECURITY ALERT: System prompt or canary token leakage detected in LLM output!");
                return new OutputValidationResult(
                    false,
                    SAFE_REFUSAL_MESSAGE,
                    "SYSTEM_PROMPT_LEAK_ATTEMPT",
                    "Output contained internal constitutional instructions or canary tokens."
                );
            }
        }

        // 2. Check for secret / credential exfiltration
        for (Pattern p : SECRET_LEAK_PATTERNS) {
            if (p.matcher(generatedOutput).find()) {
                log.error("SECURITY ALERT: Secret/credential pattern detected in LLM output!");
                return new OutputValidationResult(
                    false,
                    SAFE_REFUSAL_MESSAGE,
                    "CREDENTIAL_LEAK_ATTEMPT",
                    "Output contained potential credentials, tokens, or connection strings."
                );
            }
        }

        // 3. Check for adversarial policy compromise confirmations
        for (Pattern p : ADVERSARIAL_COMPLIANCE_PATTERNS) {
            if (p.matcher(generatedOutput).find()) {
                log.warn("SECURITY ALERT: Adversarial compliance compromise detected in output!");
                return new OutputValidationResult(
                    false,
                    SAFE_REFUSAL_MESSAGE,
                    "ADVERSARIAL_POLICY_COMPROMISE",
                    "Output contained language confirming adherence to adversarial prompt injection."
                );
            }
        }

        return new OutputValidationResult(true, generatedOutput, null, null);
    }
}
