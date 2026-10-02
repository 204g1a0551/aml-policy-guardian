import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { App } from './app';
import { AuthService } from './core/services/auth.service';

describe('App Component', () => {
  let authService: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient()
      ]
    }).compileComponents();

    authService = TestBed.inject(AuthService);
  });

  it('should create the application component', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render brand header and compliance title', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.bank-title')?.textContent).toContain('AML POLICY GUARDIAN');
    expect(compiled.querySelector('.nav-subtitle')?.textContent).toContain('Tier-1 FIU Compliance Assistant');
  });

  it('should show Sign In link when unauthenticated', () => {
    authService.logout();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.signin-link')?.textContent).toContain('Sign In');
  });

  it('should show Analyst navigation links when authenticated as Analyst', () => {
    authService.currentUser.set({
      id: '20000000-0000-0000-0000-000000000002',
      username: 'analyst',
      fullName: 'FIU Senior Investigator',
      email: 'analyst@bank.internal',
      roles: ['ROLE_ANALYST']
    });
    authService.token.set('fake-jwt-token');

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.user-name')?.textContent).toContain('FIU Senior Investigator');
    expect(compiled.querySelector('.role-badge')?.textContent?.trim()).toBe('ANALYST');

    // Should contain Chat and History links
    const links = Array.from(compiled.querySelectorAll('.nav-item')).map(el => el.textContent?.trim());
    expect(links.some(l => l?.includes('Chat'))).toBe(true);
    expect(links.some(l => l?.includes('History'))).toBe(true);

    // Should NOT contain Documents or Audit links
    expect(links.some(l => l?.includes('Documents'))).toBe(false);
    expect(links.some(l => l?.includes('Audit'))).toBe(false);
  });

  it('should show Admin navigation links when authenticated as Admin', () => {
    authService.currentUser.set({
      id: '10000000-0000-0000-0000-000000000001',
      username: 'admin',
      fullName: 'Chief Compliance Administrator',
      email: 'admin@bank.internal',
      roles: ['ROLE_ADMIN', 'ROLE_ANALYST']
    });
    authService.token.set('fake-jwt-token');

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.role-badge')?.textContent?.trim()).toBe('ADMIN');

    // Should contain Chat, History, Documents, and Audit links
    const links = Array.from(compiled.querySelectorAll('.nav-item')).map(el => el.textContent?.trim());
    expect(links.some(l => l?.includes('Chat'))).toBe(true);
    expect(links.some(l => l?.includes('History'))).toBe(true);
    expect(links.some(l => l?.includes('Documents'))).toBe(true);
    expect(links.some(l => l?.includes('Audit'))).toBe(true);
  });
});
