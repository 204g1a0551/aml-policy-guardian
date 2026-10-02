-- ============================================================================
-- V4: Add error_message column to documents table for failure recovery
-- ============================================================================

ALTER TABLE documents ADD COLUMN IF NOT EXISTS error_message TEXT;
