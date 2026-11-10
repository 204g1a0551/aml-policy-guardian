-- ============================================================================
-- V7: Regulatory Policy Versioning, Section Comparison, and Notifications
-- ============================================================================

-- 1. EXTEND DOCUMENTS TABLE FOR REGULATORY POLICY VERSIONING
ALTER TABLE documents 
    ADD COLUMN IF NOT EXISTS policy_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS jurisdiction VARCHAR(100) DEFAULT 'GLOBAL',
    ADD COLUMN IF NOT EXISTS effective_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(50) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS is_effective BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS superseded_by UUID REFERENCES documents(id),
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMP WITH TIME ZONE;

-- Populate policy_code from filename/title for existing records
UPDATE documents 
SET policy_code = CASE 
    WHEN filename LIKE 'AML-SOP-001%' THEN 'AML-SOP-001'
    WHEN filename LIKE 'AML-POL-002%' THEN 'AML-POL-002'
    WHEN filename LIKE 'AML-SOP-002%' THEN 'AML-SOP-002'
    WHEN filename LIKE 'AML-SOP-003%' THEN 'AML-SOP-003'
    WHEN filename LIKE 'AML-GUIDE-004%' THEN 'AML-GUIDE-004'
    WHEN filename LIKE 'AML-POL-005%' THEN 'AML-POL-005'
    WHEN filename LIKE 'AML-GUIDE-001%' THEN 'AML-GUIDE-001'
    ELSE COALESCE(substring(filename from '^([A-Z0-9]+-[A-Z0-9]+-[0-9]+)'), substring(title from '^([A-Z0-9]+-[A-Z0-9]+-[0-9]+)'), 'POLICY-GENERIC')
END
WHERE policy_code IS NULL;

-- Set existing documents as currently effective
UPDATE documents SET is_effective = TRUE WHERE is_effective IS NULL OR status = 'ACTIVE';

-- Performance indexes for policy version lookups
CREATE INDEX IF NOT EXISTS idx_documents_policy_code ON documents(tenant_id, policy_code);
CREATE INDEX IF NOT EXISTS idx_documents_effective ON documents(tenant_id, is_effective);
CREATE INDEX IF NOT EXISTS idx_documents_approval_status ON documents(tenant_id, approval_status);
CREATE INDEX IF NOT EXISTS idx_documents_jurisdiction ON documents(tenant_id, jurisdiction);
CREATE INDEX IF NOT EXISTS idx_documents_effective_date ON documents(tenant_id, effective_date);

-- 2. EXTEND DOCUMENT_CHUNKS FOR VERSION-AWARE RETRIEVAL
ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS policy_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS is_effective BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS effective_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Propagate policy metadata from documents to existing chunks
UPDATE document_chunks c
SET policy_code = d.policy_code,
    is_effective = d.is_effective,
    effective_date = d.effective_date
FROM documents d
WHERE c.document_id = d.id;

CREATE INDEX IF NOT EXISTS idx_document_chunks_effective ON document_chunks(tenant_id, is_effective);
CREATE INDEX IF NOT EXISTS idx_document_chunks_policy_code ON document_chunks(tenant_id, policy_code);

-- 3. POLICY SECTION COMPARISON AND REGULATORY DIFFS
CREATE TABLE IF NOT EXISTS policy_section_diffs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank',
    policy_code VARCHAR(100) NOT NULL,
    base_document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    target_document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    section_title VARCHAR(255) NOT NULL,
    change_type VARCHAR(50) NOT NULL, -- ADDITION, DELETION, MODIFIED_OBLIGATION, UNCHANGED
    base_content TEXT,
    target_content TEXT,
    regulatory_impact_summary TEXT,
    requires_human_validation BOOLEAN NOT NULL DEFAULT TRUE,
    human_validation_status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, VALIDATED, REJECTED
    validated_by UUID REFERENCES users(id),
    validated_at TIMESTAMP WITH TIME ZONE,
    validation_notes TEXT,
    affected_chunk_ids JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_policy_diffs_tenant_policy ON policy_section_diffs(tenant_id, policy_code);
CREATE INDEX IF NOT EXISTS idx_policy_diffs_documents ON policy_section_diffs(base_document_id, target_document_id);
CREATE INDEX IF NOT EXISTS idx_policy_diffs_validation ON policy_section_diffs(human_validation_status);

-- 4. COMPLIANCE CHANGE NOTIFICATIONS
CREATE TABLE IF NOT EXISTS compliance_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank',
    recipient_role VARCHAR(50) NOT NULL DEFAULT 'ROLE_COMPLIANCE_ANALYST',
    recipient_user_id UUID REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    policy_code VARCHAR(100),
    document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON compliance_notifications(tenant_id, recipient_role, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON compliance_notifications(recipient_user_id, is_read);
