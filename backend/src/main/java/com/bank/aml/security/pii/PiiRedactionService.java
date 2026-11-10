package com.bank.aml.security.pii;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PiiRedactionService {

    // US SSN: 3 digits - 2 digits - 4 digits (excluding invalid prefix 000, 666, 900-999)
    private static final Pattern SSN_PATTERN =
        Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b");

    // Credit Card PAN candidates (13 to 19 digits with optional hyphens or spaces)
    private static final Pattern CARD_PATTERN =
        Pattern.compile("\\b(?:4[0-9]{3}[- ]?[0-9]{4}[- ]?[0-9]{4}[- ]?[0-9]{1,4}|5[1-5][0-9]{2}[- ]?[0-9]{4}[- ]?[0-9]{4}[- ]?[0-9]{4}|3[47][0-9]{2}[- ]?[0-9]{6}[- ]?[0-9]{5}|6(?:011|5[0-9]{2})[- ]?[0-9]{4}[- ]?[0-9]{4}[- ]?[0-9]{4})\\b");

    // International Bank Account Number (IBAN): 2 letter country code, 2 check digits, up to 30 alphanumeric
    private static final Pattern IBAN_PATTERN =
        Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");

    // Email addresses
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b");

    // Phone numbers (US and international formats)
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]\\d{3}[-.\\s]\\d{4}\\b");

    public record RedactionResult(
        String redactedText,
        int redactionsCount,
        Set<String> redactedCategories,
        boolean piiDetected
    ) {}

    public RedactionResult redact(String text) {
        if (text == null || text.isBlank()) {
            return new RedactionResult(text, 0, Set.of(), false);
        }

        String result = text;
        Set<String> categories = new HashSet<>();
        int count = 0;

        // 1. Redact SSN
        Matcher ssnMatcher = SSN_PATTERN.matcher(result);
        if (ssnMatcher.find()) {
            categories.add("SSN");
            result = ssnMatcher.replaceAll("[REDACTED_SSN]");
            count++;
        }

        // 2. Redact Credit Card numbers with Luhn check
        Matcher cardMatcher = CARD_PATTERN.matcher(result);
        StringBuilder cardBuffer = new StringBuilder();
        int cardReplacements = 0;
        while (cardMatcher.find()) {
            String candidate = cardMatcher.group();
            String rawDigits = candidate.replaceAll("[^0-9]", "");
            if (isValidLuhn(rawDigits)) {
                cardMatcher.appendReplacement(cardBuffer, "[REDACTED_CARD]");
                cardReplacements++;
            } else {
                cardMatcher.appendReplacement(cardBuffer, Matcher.quoteReplacement(candidate));
            }
        }
        cardMatcher.appendTail(cardBuffer);
        result = cardBuffer.toString();
        if (cardReplacements > 0) {
            categories.add("CREDIT_CARD");
            count += cardReplacements;
        }

        // 3. Redact IBAN
        Matcher ibanMatcher = IBAN_PATTERN.matcher(result);
        if (ibanMatcher.find()) {
            categories.add("IBAN");
            result = ibanMatcher.replaceAll("[REDACTED_IBAN]");
            count++;
        }

        // 4. Redact Email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(result);
        if (emailMatcher.find()) {
            categories.add("EMAIL");
            result = emailMatcher.replaceAll("[REDACTED_EMAIL]");
            count++;
        }

        // 5. Redact Phone Numbers
        Matcher phoneMatcher = PHONE_PATTERN.matcher(result);
        if (phoneMatcher.find()) {
            categories.add("PHONE");
            result = phoneMatcher.replaceAll("[REDACTED_PHONE]");
            count++;
        }

        return new RedactionResult(result, count, categories, count > 0);
    }

    /**
     * Standard Luhn checksum algorithm (Mod 10) to prevent false-positive card masking.
     */
    public static boolean isValidLuhn(String digits) {
        if (digits == null || digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (n < 0 || n > 9) return false;
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
