-- ============================================================================
-- V5: Hybrid Search, PostgreSQL Full-Text Search tsvector, and Parent-Child Chunk Hierarchy
-- ============================================================================

-- 1. Add parent chunk references for hierarchical chunk retrieval
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS parent_chunk_id UUID REFERENCES document_chunks(id) ON DELETE CASCADE;
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS is_parent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS chunk_summary TEXT;

CREATE INDEX IF NOT EXISTS idx_document_chunks_parent_id ON document_chunks(parent_chunk_id);
CREATE INDEX IF NOT EXISTS idx_document_chunks_is_parent ON document_chunks(is_parent);

-- 2. Add generated full-text search tsvector column combining section and chunk text
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS text_search_tsv tsvector 
GENERATED ALWAYS AS (to_tsvector('english', coalesce(section, '') || ' ' || chunk_text)) STORED;

-- 3. Create GIN index for high-performance lexical full-text search
CREATE INDEX IF NOT EXISTS idx_document_chunks_tsv ON document_chunks USING gin (text_search_tsv);
