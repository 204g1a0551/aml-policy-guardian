package com.bank.aml.document.parser;

import com.bank.aml.exception.DocumentProcessingException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class TikaDocumentParser implements DocumentParser {

    private final AutoDetectParser parser = new AutoDetectParser();

    @Override
    public ExtractedDocument parse(InputStream inputStream, String filename, String mimeType) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1); // Unlimited character buffer
            Metadata metadata = new Metadata();
            if (filename != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            if (mimeType != null) {
                metadata.set(Metadata.CONTENT_TYPE, mimeType);
            }

            ParseContext context = new ParseContext();
            parser.parse(inputStream, handler, metadata, context);

            String fullText = handler.toString().trim();
            if (fullText.isEmpty()) {
                throw new DocumentProcessingException("No readable text content extracted from document: " + filename);
            }

            List<ExtractedPage> pages = extractPages(fullText);
            return new ExtractedDocument(fullText, pages);

        } catch (DocumentProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to extract text from document " + filename + ": " + e.getMessage(), e);
        }
    }

    private List<ExtractedPage> extractPages(String fullText) {
        List<ExtractedPage> pages = new ArrayList<>();

        // Tika uses form-feed character (\f) to represent page breaks in PDFs and paginated docs
        if (fullText.contains("\f")) {
            String[] rawPages = fullText.split("\f");
            int pageNum = 1;
            for (String p : rawPages) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    pages.add(new ExtractedPage(pageNum++, trimmed));
                }
            }
        }

        // If no form feeds were detected, treat the entire document as page 1
        if (pages.isEmpty()) {
            pages.add(new ExtractedPage(1, fullText));
        }

        return pages;
    }
}
