# AML-SOP-002: High-Value Transaction Monitoring and Alert Investigation Standard Operating Procedure

**Document Code:** AML-SOP-002  
**Version:** v2.4  
**Effective Date:** 2026-02-01  
**Classification:** Standard Operating Procedure (SOP)  
**Department:** Financial Intelligence Unit (FIU)  

---

## 1. Trigger Definition
A High-Value Transaction Alert (Scenario Code: `ALRT_HVT_01`) is triggered in the transaction monitoring system when:
1. An individual aggregate transfer within 24 hours exceeds $50,000 USD (or foreign currency equivalent).
2. A single wire transfer exceeds $10,000 USD to a newly added beneficiary or offshore destination.
3. Structuring indicator: Multiple consecutive deposits between $9,000 and $9,999 within 5 consecutive business days.

## 2. Step-by-Step Investigation Workflow for Analysts

When a customer triggers a high-value transaction alert, the compliance analyst must complete the following six steps:

### Step 1: Alert Triage and Profile Review (Within 4 Hours)
- Open the alert in the AML Case Management system.
- Review the customer's Know Your Customer (KYC) baseline profile, documented expected monthly transaction volumes, and primary industry.
- Confirm whether the transaction aligns with the established transaction baseline.

### Step 2: Source and Destination Analysis (Within 12 Hours)
- Identify the ordering party and beneficiary account, institution, routing code, and geographic corridor.
- Screen both originator and beneficiary against OFAC, EU, UN, and PEP watchlists.
- If the transfer involves a high-risk jurisdiction, immediately flag the transaction for Enhanced Due Diligence (EDD).

### Step 3: Economic Rationale and Supporting Documentation (Within 24 Hours)
- Inspect available transaction memos, invoice attachments, and purpose-of-payment notations.
- If the economic rationale is ambiguous or unverified, issue a Request for Information (RFI) to the relationship manager (RM) or front office.
- The RM has 2 business days to provide verified invoices, purchase agreements, or audited financial statements.

### Step 4: Transaction Pattern and Structuring Assessment
- Review historical transaction records covering the prior 180 days.
- Analyze if the transaction represents rapid movement of funds (pass-through account behavior), circular fund flow, or sudden spikes following dormant periods.

### Step 5: Decision Determination
- **True Positive / Suspicious:** If documentation is unverified, rationale is fraudulent, or structuring is identified, escalate to the Senior FIU Manager with a recommendation to file a Suspicious Activity Report (SAR).
- **False Positive:** If documentation legitimately justifies the transaction volume (e.g., real estate closing, verified commercial dividend), close the alert with written justification and save all supporting documents in the audit repository.

### Step 6: Documentation and Audit Trail
- Log all investigative steps, rationale, and timestamps in the AML Case Management dossier.
- All high-value alert cases must be preserved in accordance with the bank's 7-year regulatory retention policy.
