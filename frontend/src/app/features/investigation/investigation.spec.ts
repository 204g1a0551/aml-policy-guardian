import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { InvestigationComponent } from './investigation.component';
import { InvestigationService } from '../../core/services/investigation.service';
import { AuthService } from '../../core/services/auth.service';
import { AmlAlert, AmlInvestigationDetail } from '../../core/models/investigation.models';

describe('InvestigationComponent (Phase 5)', () => {
  let component: InvestigationComponent;
  let fixture: ComponentFixture<InvestigationComponent>;
  let investigationService: InvestigationService;
  let authService: AuthService;

  const mockAlert: AmlAlert = {
    id: 'alert-1',
    alertReference: 'ALRT-2026-1001',
    scenarioCode: 'TM-RULE-101',
    scenarioTitle: 'Structuring Cash Deposits',
    customerId: 'CUST-100',
    customerName: 'Summit Auto Imports',
    customerRiskLevel: 'HIGH',
    accountNumber: 'ACCT-7739',
    totalAlertAmount: 28500,
    currency: 'USD',
    severity: 'HIGH',
    status: 'NEW',
    triggeredAt: new Date().toISOString(),
    tenantId: 'default-bank',
    createdAt: new Date().toISOString(),
    transactions: [
      {
        id: 'txn-1',
        transactionReference: 'TXN-101',
        transactionDate: new Date().toISOString(),
        amount: 9500,
        currency: 'USD',
        transactionType: 'CASH_DEPOSIT',
        counterpartyName: 'Branch Teller',
        counterpartyCountry: 'USA',
        suspiciousIndicator: true
      }
    ]
  };

  const mockInvestigation: AmlInvestigationDetail = {
    id: 'inv-1',
    investigationReference: 'INV-2026-1001',
    alert: mockAlert,
    status: 'DRAFT_READY',
    aiSummary: 'Structuring cash deposits identified below $10,000 threshold.',
    policyRelevanceExplanation: 'AML-SOP-001 Section 3.2 applies.',
    sarNarrativeDraft: 'Section I: Subject Profile...',
    decisionRecommendation: 'FILE_SAR',
    linkedPolicies: [
      {
        policyCode: 'AML-SOP-001',
        documentTitle: 'Transaction Monitoring Procedure',
        section: 'Section 3.2',
        snippet: 'Multiple cash deposits under $10,000 within 72 hours.',
        relevanceScore: 0.94
      }
    ],
    linkedAuditHashes: ['hash-112233'],
    evidenceQualityScore: 0.88,
    evidenceLevel: 'HIGH',
    tenantId: 'default-bank',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    timeline: [
      {
        id: 'tl-1',
        action: 'AI_ANALYSIS_GENERATED',
        performedBy: 'SYSTEM_AI_ASSISTANT',
        notes: 'Initial SAR draft generated',
        previousStatus: 'PENDING_ANALYSIS',
        newStatus: 'DRAFT_READY',
        recordedAt: new Date().toISOString()
      }
    ]
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InvestigationComponent],
      providers: [provideHttpClient()]
    }).compileComponents();

    fixture = TestBed.createComponent(InvestigationComponent);
    component = fixture.componentInstance;
    investigationService = TestBed.inject(InvestigationService);
    authService = TestBed.inject(AuthService);

    vi.spyOn(investigationService, 'getAlerts').mockReturnValue(of({
      content: [mockAlert],
      totalElements: 1,
      totalPages: 1,
      size: 20,
      number: 0
    }));

    vi.spyOn(investigationService, 'getInvestigations').mockReturnValue(of({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 20,
      number: 0
    }));

    fixture.detectChanges();
  });

  it('should initialize and load alerts queue', () => {
    expect(component).toBeTruthy();
    expect(component.alerts().length).toBe(1);
    expect(component.alerts()[0].alertReference).toBe('ALRT-2026-1001');
    expect(component.activeTab()).toBe('alerts');
  });

  it('should switch tabs between alerts and history', () => {
    component.setTab('history');
    expect(component.activeTab()).toBe('history');

    component.setTab('alerts');
    expect(component.activeTab()).toBe('alerts');
  });

  it('should start investigation and transition to workspace with SAR draft', () => {
    const analyzeSpy = vi.spyOn(investigationService, 'analyzeAlert').mockReturnValue(of(mockInvestigation));

    component.startInvestigation('alert-1');

    expect(analyzeSpy).toHaveBeenCalledWith('alert-1');
    expect(component.activeTab()).toBe('workspace');
    expect(component.selectedInvestigation()).toEqual(mockInvestigation);
    expect(component.editableDraft).toContain('Section I: Subject Profile');
    expect(component.selectedInvestigation()?.evidenceLevel).toBe('HIGH');
  });

  it('should submit analyst decision and record disposition', () => {
    component.selectedInvestigation.set(mockInvestigation);
    component.editableDraft = mockInvestigation.sarNarrativeDraft || '';
    const updatedInv = { ...mockInvestigation, status: 'UNDER_REVIEW', analystDecision: 'ACCEPTED' };
    const decisionSpy = vi.spyOn(investigationService, 'submitAnalystDecision').mockReturnValue(of(updatedInv));

    component.submitAnalystDecision('ACCEPTED');

    expect(decisionSpy).toHaveBeenCalledWith('inv-1', {
      decision: 'ACCEPTED',
      updatedDraft: mockInvestigation.sarNarrativeDraft,
      notes: ''
    });
    expect(component.selectedInvestigation()?.status).toBe('UNDER_REVIEW');
  });

  it('should verify reviewer permissions based on user roles', () => {
    authService.currentUser.set({
      id: 'user-1',
      username: 'analyst',
      email: 'analyst@bank.com',
      fullName: 'Analyst User',
      roles: ['ROLE_COMPLIANCE_ANALYST']
    });
    expect(component.canReview()).toBe(false);

    authService.currentUser.set({
      id: 'admin-1',
      username: 'admin',
      email: 'admin@bank.com',
      fullName: 'Admin User',
      roles: ['ROLE_ADMIN']
    });
    expect(component.canReview()).toBe(true);
  });
});
