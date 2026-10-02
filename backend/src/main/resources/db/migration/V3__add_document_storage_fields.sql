-- ============================================================================
-- V3: Add Document Storage, Checksum, and MIME fields
-- ============================================================================

ALTER TABLE documents ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT DEFAULT 0;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS sha256_checksum VARCHAR(64);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_path VARCHAR(500);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_documents_sha256 ON documents(sha256_checksum);
