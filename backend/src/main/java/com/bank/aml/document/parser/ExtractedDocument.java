package com.bank.aml.document.parser;

import java.util.List;

public record ExtractedDocument(
    String rawText,
    List<ExtractedPage> pages
) {}
