package com.bank.aml.document.parser;

import java.io.InputStream;

public interface DocumentParser {
    ExtractedDocument parse(InputStream inputStream, String filename, String mimeType);
}
