package com.bank.aml.document.parser;

public record ExtractedPage(
    int pageNumber,
    String text
) {}
