package com.bank.aml.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String DOCUMENT_UPLOADED = "aml.document.uploaded";
    public static final String INGESTION_REQUESTED = "aml.document.ingestion";
    public static final String INDEXING_COMPLETED = "aml.document.indexing.completed";
    public static final String INDEXING_FAILED = "aml.document.indexing.failed";
    public static final String INDEXING_DLT = "aml.document.indexing.dlt";

    public static final String INGESTION_CONSUMER_GROUP = "aml-document-indexing-group";
}
