-- Phase 6: Document Ingestion Jobs & Kafka Event Processing Tracking

CREATE TABLE IF NOT EXISTS document_ingestion_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id VARCHAR(100) NOT NULL DEFAULT 'default-bank',
    job_type VARCHAR(50) NOT NULL DEFAULT 'DOCUMENT_INGESTION',
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    stage VARCHAR(100) NOT NULL DEFAULT 'QUEUED',
    progress_pct INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_tenant_doc ON document_ingestion_jobs(tenant_id, document_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_status ON document_ingestion_jobs(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_created ON document_ingestion_jobs(created_at DESC);

-- Consumer Idempotency and Event Deduplication Store
CREATE TABLE IF NOT EXISTS kafka_processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL DEFAULT 'default-bank',
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kafka_events_processed ON kafka_processed_events(processed_at);
