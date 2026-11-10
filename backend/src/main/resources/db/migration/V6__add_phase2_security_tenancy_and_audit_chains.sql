-- ============================================================================
-- V6: Phase 2 Enterprise Security, Multi-Tenant Isolation, RBAC & Audit Hash Chains
-- ============================================================================

-- 1. ADD TENANT ISOLATION COLUMNS TO REPOSITORIES
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank';
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank';
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank';

-- 2. ADD TAMPER-EVIDENT HASH CHAINING TO AUDIT LOGS
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS sequence_number BIGSERIAL;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64) NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000';
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64) NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000';

-- 3. INDEXES FOR MULTI-TENANT QUERYING & AUDIT INTEGRITY VERIFICATION
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_documents_tenant_id ON documents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_document_chunks_tenant_id ON document_chunks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_tenant_id ON chat_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_chain ON audit_logs(tenant_id, sequence_number ASC);

-- 4. RBAC EXTENSIONS: ADD AUDITOR & COMPLIANCE_ANALYST ROLES
INSERT INTO roles (id, name, description)
VALUES 
    ('10000000-0000-0000-0000-000000000003', 'ROLE_AUDITOR', 'Compliance Auditor with read-only access to audit logs and chain verification'),
    ('10000000-0000-0000-0000-000000000004', 'ROLE_COMPLIANCE_ANALYST', 'Standardized compliance analyst role for inquiry and investigation')
ON CONFLICT (name) DO NOTHING;

-- 5. DEFAULT AUDITOR USER (auditor / AuditorPass123!)
INSERT INTO users (id, username, password_hash, full_name, email, enabled, tenant_id)
VALUES 
    ('20000000-0000-0000-0000-000000000003', 'auditor', '$2a$12$tVkhlbkpsQrZwvMfyEIUZ.oynn6PuqshhLqHnMYjfOsX929YGYUjq', 'External Compliance Auditor', 'auditor@bank.internal', TRUE, 'default-bank')
ON CONFLICT (username) DO NOTHING;

-- Assign ROLE_AUDITOR to auditor
INSERT INTO user_roles (user_id, role_id)
VALUES 
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

-- Assign ROLE_COMPLIANCE_ANALYST to analyst and admin
INSERT INTO user_roles (user_id, role_id)
VALUES 
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004')
ON CONFLICT DO NOTHING;

-- 6. SECURITY ALERTS TABLE FOR THREAT INTELLIGENCE AND AUDITING
CREATE TABLE IF NOT EXISTS security_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL DEFAULT 'default-bank',
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    client_ip VARCHAR(100),
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_alerts_tenant_created ON security_alerts(tenant_id, created_at DESC);
