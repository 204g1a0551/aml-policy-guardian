-- ============================================================================
-- V2: Synthetic Seed Data for AML Assistant
-- Roles, Default Users, Sample Documents, Synthetic Chunks with 1536-dim vectors
-- ============================================================================

-- 1. ROLES
INSERT INTO roles (id, name, description)
VALUES 
    ('10000000-0000-0000-0000-000000000001', 'ROLE_ADMIN', 'Compliance Administrator with document ingestion and user management rights'),
    ('10000000-0000-0000-0000-000000000002', 'ROLE_ANALYST', 'FIU Compliance Analyst with inquiry and investigation rights')
ON CONFLICT (name) DO NOTHING;

-- 2. DEFAULT USERS (BCrypt cost 12 passwords)
-- Admin: admin / AdminPass123!
-- Analyst: analyst / AnalystPass123!
INSERT INTO users (id, username, password_hash, full_name, email, enabled)
VALUES 
    ('20000000-0000-0000-0000-000000000001', 'admin', '$2a$12$tVkhlbkpsQrZwvMfyEIUZ.oynn6PuqshhLqHnMYjfOsX929YGYUjq', 'AML Compliance Admin', 'admin@bank.internal', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'analyst', '$2a$12$tVkhlbkpsQrZwvMfyEIUZ.oynn6PuqshhLqHnMYjfOsX929YGYUjq', 'FIU Senior Analyst', 'analyst@bank.internal', TRUE)
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;

-- 3. ASSIGN USER ROLES
INSERT INTO user_roles (user_id, role_id)
VALUES 
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'), -- admin -> ROLE_ADMIN
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002'), -- admin -> ROLE_ANALYST
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002')  -- analyst -> ROLE_ANALYST
ON CONFLICT DO NOTHING;

-- 4. SAMPLE AML POLICY DOCUMENT
INSERT INTO documents (id, title, filename, document_type, version, source, status, uploaded_by)
VALUES 
    ('30000000-0000-0000-0000-000000000001', 
     'High-Value Transaction Monitoring and Alert Investigation Standard Operating Procedure', 
     'AML-SOP-002_High_Value_Transaction_Monitoring.md', 
     'SOP', 
     'v2.4', 
     'FIU Compliance Governance', 
     'ACTIVE', 
     '20000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- 5. SYNTHETIC DOCUMENT CHUNKS WITH 1536-DIM VECTORS
INSERT INTO document_chunks (id, document_id, chunk_text, chunk_index, page_number, section, metadata, embedding)
VALUES 
    ('40000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000001',
     'A High-Value Transaction Alert (Scenario Code: ALRT_HVT_01) is triggered when an aggregate transfer within 24 hours exceeds $50,000 USD, a single wire exceeds $10,000 USD to a new beneficiary, or structuring between $9,000 and $9,999 is detected within 5 consecutive business days.',
     0,
     1,
     'Section 1: Trigger Definition',
     '{"documentCode": "AML-SOP-002", "category": "MONITORING", "version": "v2.4"}'::jsonb,
     array_fill(0.0255155, ARRAY[1536])::vector),

    ('40000000-0000-0000-0000-000000000002',
     '30000000-0000-0000-0000-000000000001',
     'When a customer triggers a high-value transaction alert, the compliance analyst must complete: Step 1: Alert Triage and Profile Review (within 4 hours); Step 2: Source and Destination Analysis (within 12 hours); Step 3: Economic Rationale and Supporting Documentation (within 24 hours); Step 4: Transaction Pattern and Structuring Assessment; Step 5: Decision Determination (True Positive or False Positive); Step 6: Documentation and Audit Trail.',
     1,
     2,
     'Section 2: Step-by-Step Investigation Workflow for Analysts',
     '{"documentCode": "AML-SOP-002", "category": "INVESTIGATION", "version": "v2.4"}'::jsonb,
     array_fill(0.0255155, ARRAY[1536])::vector)
ON CONFLICT DO NOTHING;

-- 6. SAMPLE CHAT SESSION & MESSAGES
INSERT INTO chat_sessions (id, user_id, title)
VALUES 
    ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 'High-Value Alert Investigation Query')
ON CONFLICT DO NOTHING;

INSERT INTO chat_messages (id, session_id, role, content)
VALUES 
    ('60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'USER', 'What steps should I follow when a customer triggers a high-value transaction alert?'),
    ('60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', 'ASSISTANT', 'When a customer triggers a high-value transaction alert, follow these 6 mandatory steps: Step 1 (Alert Triage), Step 2 (Source and Destination Analysis), Step 3 (Economic Rationale), Step 4 (Pattern Assessment), Step 5 (Decision), and Step 6 (Documentation) [[AML-SOP-002, p.2]].')
ON CONFLICT DO NOTHING;

-- 7. SAMPLE AUDIT LOG
INSERT INTO audit_logs (id, user_id, action, resource_type, resource_id, request_id, metadata)
VALUES 
    ('70000000-0000-0000-0000-000000000001', 
     '20000000-0000-0000-0000-000000000002', 
     'INVESTIGATION_QUERY', 
     'DOCUMENT_CHUNK', 
     '40000000-0000-0000-0000-000000000002', 
     'REQ-TEST-001', 
     '{"question": "What steps should I follow when a customer triggers a high-value transaction alert?", "retrievedChunks": 2, "latencyMs": 350}'::jsonb)
ON CONFLICT DO NOTHING;
