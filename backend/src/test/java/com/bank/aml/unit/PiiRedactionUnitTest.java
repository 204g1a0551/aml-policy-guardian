package com.bank.aml.unit;

import com.bank.aml.security.pii.PiiRedactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactionUnitTest {

    private PiiRedactionService piiService;

    @BeforeEach
    void setUp() {
        piiService = new PiiRedactionService();
    }

    @Test
    @DisplayName("PII: Successfully redacts US Social Security Numbers")
    void testSsnRedaction() {
        String input = "Customer John Doe with SSN 123-45-6789 opened a high-risk offshore account.";
        PiiRedactionService.RedactionResult result = piiService.redact(input);

        assertThat(result.piiDetected()).isTrue();
        assertThat(result.redactedCategories()).contains("SSN");
        assertThat(result.redactedText()).doesNotContain("123-45-6789");
        assertThat(result.redactedText()).contains("[REDACTED_SSN]");
    }

    @Test
    @DisplayName("PII: Validates and redacts Luhn-compliant Credit Card numbers while ignoring invalid digits")
    void testCreditCardLuhnRedaction() {
        // Valid Visa test card (passes Luhn)
        String validVisa = "4539-1488-0343-6467";
        // Invalid numbers (fails Luhn)
        String invalidCard = "4539-1488-0343-6468";

        String input = "Deposits made with cards: Valid=" + validVisa + " and Invalid=" + invalidCard;
        PiiRedactionService.RedactionResult result = piiService.redact(input);

        assertThat(result.piiDetected()).isTrue();
        assertThat(result.redactedCategories()).contains("CREDIT_CARD");
        assertThat(result.redactedText()).doesNotContain(validVisa);
        assertThat(result.redactedText()).contains("[REDACTED_CARD]");
        // The invalid card is preserved without false positive masking
        assertThat(result.redactedText()).contains(invalidCard);
    }

    @Test
    @DisplayName("PII: Successfully redacts International Bank Account Numbers (IBAN)")
    void testIbanRedaction() {
        String input = "Wire sent from GB82WEST12345698765432 to beneficiary in Geneva.";
        PiiRedactionService.RedactionResult result = piiService.redact(input);

        assertThat(result.piiDetected()).isTrue();
        assertThat(result.redactedCategories()).contains("IBAN");
        assertThat(result.redactedText()).doesNotContain("GB82WEST12345698765432");
        assertThat(result.redactedText()).contains("[REDACTED_IBAN]");
    }

    @Test
    @DisplayName("PII: Successfully redacts personal email addresses and phone numbers")
    void testEmailAndPhoneRedaction() {
        String input = "Contact target at jane.compliance@external-bank.com or call +1 202-555-0199 for SAR filing.";
        PiiRedactionService.RedactionResult result = piiService.redact(input);

        assertThat(result.piiDetected()).isTrue();
        assertThat(result.redactedCategories()).contains("EMAIL", "PHONE");
        assertThat(result.redactedText()).doesNotContain("jane.compliance@external-bank.com");
        assertThat(result.redactedText()).contains("[REDACTED_EMAIL]");
        assertThat(result.redactedText()).contains("[REDACTED_PHONE]");
    }

    @Test
    @DisplayName("PII: Clean text without PII returns unmodified with zero detections")
    void testCleanTextHasNoRedaction() {
        String input = "Currency Transaction Reporting (CTR) applies to cash deposits exceeding $10,000 USD.";
        PiiRedactionService.RedactionResult result = piiService.redact(input);

        assertThat(result.piiDetected()).isFalse();
        assertThat(result.redactedText()).isEqualTo(input);
        assertThat(result.redactionsCount()).isEqualTo(0);
    }
}
