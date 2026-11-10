-- V8__add_aml_investigation_workspace.sql
-- Establishes Phase 5 AML Investigation Workspace, Synthetic Transaction Monitoring Alerts,
-- Policy Citation Linking, Human-in-the-Loop Review State Machine, and Audit Trail.

-- 1. Synthetic Transaction Monitoring Alerts Table
CREATE TABLE IF NOT EXISTS aml_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_reference VARCHAR(64) NOT NULL UNIQUE,
    scenario_code VARCHAR(64) NOT NULL,
    scenario_title VARCHAR(255) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_risk_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    account_number VARCHAR(64) NOT NULL,
    total_alert_amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_aml_alerts_tenant_status ON aml_alerts(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_aml_alerts_scenario ON aml_alerts(scenario_code);
CREATE INDEX IF NOT EXISTS idx_aml_alerts_customer ON aml_alerts(customer_id);

-- 2. Synthetic Transactions Table
CREATE TABLE IF NOT EXISTS aml_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES aml_alerts(id) ON DELETE CASCADE,
    transaction_reference VARCHAR(64) NOT NULL,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    transaction_type VARCHAR(64) NOT NULL,
    counterparty_name VARCHAR(255) NOT NULL,
    counterparty_country VARCHAR(64) NOT NULL DEFAULT 'USA',
    counterparty_account VARCHAR(64),
    narrative TEXT,
    is_suspicious_indicator BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_aml_transactions_alert ON aml_transactions(alert_id);

-- 3. AML Investigations Table
CREATE TABLE IF NOT EXISTS aml_investigations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL UNIQUE REFERENCES aml_alerts(id) ON DELETE CASCADE,
    investigation_reference VARCHAR(64) NOT NULL UNIQUE,
    assigned_analyst VARCHAR(128),
    reviewer VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ANALYSIS',
    ai_summary TEXT,
    policy_relevance_explanation TEXT,
    sar_narrative_draft TEXT,
    decision_recommendation VARCHAR(64),
    analyst_decision VARCHAR(64),
    analyst_notes TEXT,
    reviewer_decision VARCHAR(64),
    reviewer_notes TEXT,
    escalation_reason TEXT,
    linked_policies TEXT,
    linked_audit_hashes TEXT,
    evidence_quality_score DOUBLE PRECISION DEFAULT 0.0,
    evidence_level VARCHAR(32) DEFAULT 'MEDIUM',
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_aml_investigations_tenant_status ON aml_investigations(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_aml_investigations_ref ON aml_investigations(investigation_reference);

-- 4. Investigation Decision & Review Timeline
CREATE TABLE IF NOT EXISTS aml_investigation_timeline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id UUID NOT NULL REFERENCES aml_investigations(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL,
    performed_by VARCHAR(128) NOT NULL,
    notes TEXT,
    previous_status VARCHAR(32),
    new_status VARCHAR(32),
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_investigation_timeline_inv ON aml_investigation_timeline(investigation_id);

-- 5. Seed Pre-Configured Fictional AML Alert Scenarios
INSERT INTO aml_alerts (
    id, alert_reference, scenario_code, scenario_title, customer_id, customer_name,
    customer_risk_level, account_number, total_alert_amount, currency, severity,
    status, triggered_at, tenant_id
) VALUES
(
    'a1000000-0000-0000-0000-000000000001',
    'ALRT-2026-1001',
    'TM-RULE-101',
    'Multiple Cash Deposits Below Statutory CTR Threshold (Structuring)',
    'CUST-88301',
    'Summit Premier Auto Imports LLC',
    'HIGH',
    'ACCT-77391024',
    28500.00,
    'USD',
    'HIGH',
    'NEW',
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    'default-bank'
),
(
    'a1000000-0000-0000-0000-000000000002',
    'ALRT-2026-1002',
    'ALRT_HVT_01',
    'High-Value Outbound Cross-Border Wire to High-Risk Jurisdiction',
    'CUST-99412',
    'Vanguard Global Trade Partners Inc',
    'HIGH',
    'ACCT-44109281',
    185000.00,
    'USD',
    'CRITICAL',
    'NEW',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    'default-bank'
),
(
    'a1000000-0000-0000-0000-000000000003',
    'SANCTION-PEP-03',
    'SANCTION-PEP-03',
    'Rapid Movement of Funds Involving Politically Exposed Person (PEP) Entity',
    'CUST-55109',
    'Elysium Strategic Advisory Group',
    'HIGH',
    'ACCT-11209348',
    450000.00,
    'USD',
    'CRITICAL',
    'NEW',
    CURRENT_TIMESTAMP - INTERVAL '18 hours',
    'default-bank'
),
(
    'a1000000-0000-0000-0000-000000000004',
    'ALRT-2026-1004',
    'TM-RULE-204',
    'Sudden Velocity and Volume Surge Inconsistent with Customer Profile',
    'CUST-33821',
    'Elena Rostova (Retail Account)',
    'MEDIUM',
    'ACCT-99201455',
    89500.00,
    'USD',
    'MEDIUM',
    'NEW',
    CURRENT_TIMESTAMP - INTERVAL '6 hours',
    'default-bank'
)
ON CONFLICT (alert_reference) DO NOTHING;

-- Seed Fictional Transactions for Alert 1: Structuring
INSERT INTO aml_transactions (
    id, alert_id, transaction_reference, transaction_date, amount, currency,
    transaction_type, counterparty_name, counterparty_country, counterparty_account,
    narrative, is_suspicious_indicator
) VALUES
(
    'b1000000-0000-0000-0000-000000000011',
    'a1000000-0000-0000-0000-000000000001',
    'TXN-2026-0101',
    CURRENT_TIMESTAMP - INTERVAL '48 hours',
    9500.00,
    'USD',
    'CASH_DEPOSIT',
    'Branch #14 Teller 3',
    'USA',
    'CASH',
    'Over-the-counter cash deposit at North Branch teller',
    TRUE
),
(
    'b1000000-0000-0000-0000-000000000012',
    'a1000000-0000-0000-0000-000000000001',
    'TXN-2026-0102',
    CURRENT_TIMESTAMP - INTERVAL '30 hours',
    9500.00,
    'USD',
    'CASH_DEPOSIT',
    'Branch #08 ATM #2',
    'USA',
    'CASH',
    'Cash deposit at Downtown ATM facility',
    TRUE
),
(
    'b1000000-0000-0000-0000-000000000013',
    'a1000000-0000-0000-0000-000000000001',
    'TXN-2026-0103',
    CURRENT_TIMESTAMP - INTERVAL '12 hours',
    9500.00,
    'USD',
    'CASH_DEPOSIT',
    'Branch #22 Drive-thru',
    'USA',
    'CASH',
    'Drive-thru currency deposit just below CTR threshold',
    TRUE
)
ON CONFLICT DO NOTHING;

-- Seed Fictional Transactions for Alert 2: High Value Offshore Wire
INSERT INTO aml_transactions (
    id, alert_id, transaction_reference, transaction_date, amount, currency,
    transaction_type, counterparty_name, counterparty_country, counterparty_account,
    narrative, is_suspicious_indicator
) VALUES
(
    'b1000000-0000-0000-0000-000000000021',
    'a1000000-0000-0000-0000-000000000002',
    'TXN-2026-0201',
    CURRENT_TIMESTAMP - INTERVAL '26 hours',
    185000.00,
    'USD',
    'WIRE_TRANSFER_OUTBOUND',
    'Bayshore Marine Capital Ltd',
    'BVI',
    'BVI99881122',
    'Commercial consultancy payment for maritime asset acquisition',
    TRUE
),
(
    'b1000000-0000-0000-0000-000000000022',
    'a1000000-0000-0000-0000-000000000002',
    'TXN-2026-0202',
    CURRENT_TIMESTAMP - INTERVAL '36 hours',
    190000.00,
    'USD',
    'ACH_CREDIT',
    'Domestic Client Invoice Settlement',
    'USA',
    'US11002299',
    'Inflow of funds from domestic customer settlement',
    FALSE
)
ON CONFLICT DO NOTHING;

-- Seed Fictional Transactions for Alert 3: PEP Rapid Movement
INSERT INTO aml_transactions (
    id, alert_id, transaction_reference, transaction_date, amount, currency,
    transaction_type, counterparty_name, counterparty_country, counterparty_account,
    narrative, is_suspicious_indicator
) VALUES
(
    'b1000000-0000-0000-0000-000000000031',
    'a1000000-0000-0000-0000-000000000003',
    'TXN-2026-0301',
    CURRENT_TIMESTAMP - INTERVAL '20 hours',
    450000.00,
    'USD',
    'WIRE_TRANSFER_INBOUND',
    'Caspian Energy Holdings SA',
    'Switzerland',
    'CH8899001122',
    'Advisory retainer from PEP-linked foreign energy enterprise',
    TRUE
),
(
    'b1000000-0000-0000-0000-000000000032',
    'a1000000-0000-0000-0000-000000000003',
    'TXN-2026-0302',
    CURRENT_TIMESTAMP - INTERVAL '14 hours',
    225000.00,
    'USD',
    'WIRE_TRANSFER_OUTBOUND',
    'Aethelgard Real Estate Investments LLC',
    'UAE',
    'AE44332211',
    'Immediate outward transmission to Dubai property escrow',
    TRUE
),
(
    'b1000000-0000-0000-0000-000000000033',
    'a1000000-0000-0000-0000-000000000003',
    'TXN-2026-0303',
    CURRENT_TIMESTAMP - INTERVAL '12 hours',
    220000.00,
    'USD',
    'WIRE_TRANSFER_OUTBOUND',
    'Monaco Yacht Services SARL',
    'Monaco',
    'MC12345678',
    'Immediate outward transmission to European luxury charter escrow',
    TRUE
)
ON CONFLICT DO NOTHING;
