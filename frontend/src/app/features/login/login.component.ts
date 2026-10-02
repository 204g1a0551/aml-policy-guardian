import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-wrapper">
      <div class="compliance-banner">
        <div class="banner-inner">
          <svg class="lock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
          <span>
            <strong>CONFIDENTIAL REGULATORY SYSTEM:</strong> Authorized financial intelligence and compliance personnel only.
            All sessions, queries, and document access are cryptographically signed, timestamped, and retained in accordance with BSA/AML federal regulations.
          </span>
        </div>
      </div>

      <div class="login-card">
        <div class="card-header">
          <div class="crest-box">
            <svg class="crest-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              <path d="M9 12l2 2 4-4"/>
            </svg>
          </div>
          <h2>Financial Intelligence Unit</h2>
          <p class="subtitle">AML Policy & Transaction Investigation Gateway</p>
        </div>

        <form (ngSubmit)="onSubmit()" class="login-form">
          <div class="form-field">
            <label for="username">Investigator / User ID</label>
            <input 
              id="username" 
              name="username" 
              type="text" 
              [(ngModel)]="username" 
              required 
              autocomplete="username"
              placeholder="e.g. analyst or admin"
            />
          </div>

          <div class="form-field">
            <div class="label-row">
              <label for="password">Password</label>
              <span class="field-hint">Internal Credentials</span>
            </div>
            <input 
              id="password" 
              name="password" 
              type="password" 
              [(ngModel)]="password" 
              required 
              autocomplete="current-password"
              placeholder="Enter secure password"
            />
          </div>

          @if (errorMessage()) {
            <div class="error-banner">
              <svg class="alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <button type="submit" [disabled]="isLoading()" class="btn-submit">
            @if (isLoading()) {
              <span>Authenticating Credentials...</span>
            } @else {
              <span>Sign In to Compliance Gateway</span>
            }
          </button>
        </form>

        <div class="system-notice">
          <div class="notice-title">Evaluation & Testing Credentials</div>
          <div class="credential-selector">
            <button type="button" class="cred-link" (click)="fillCredentials('analyst', 'AdminPass123!')">
              Use Senior FIU Analyst (analyst)
            </button>
            <span class="cred-sep">&bull;</span>
            <button type="button" class="cred-link" (click)="fillCredentials('admin', 'AdminPass123!')">
              Use Compliance Admin (admin)
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-wrapper {
      min-height: calc(100vh - 56px);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
      background-color: #f8fafc;
    }

    .compliance-banner {
      width: 100%;
      max-width: 680px;
      margin-bottom: 1.5rem;
      background-color: #fffbeb;
      border: 1px solid #fde68a;
      border-radius: 4px;
      padding: 0.75rem 1rem;
    }

    .banner-inner {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      font-size: 0.775rem;
      color: #92400e;
      line-height: 1.45;
    }

    .lock-icon {
      width: 16px;
      height: 16px;
      flex-shrink: 0;
      margin-top: 2px;
      color: #b45309;
    }

    .login-card {
      width: 100%;
      max-width: 440px;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 2.25rem 2rem;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.03);
    }

    .card-header {
      text-align: center;
      margin-bottom: 1.75rem;
    }

    .crest-box {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 48px;
      height: 48px;
      background-color: #f1f5f9;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      color: #1e3a8a;
      margin-bottom: 0.75rem;
    }

    .crest-icon {
      width: 26px;
      height: 26px;
    }

    .card-header h2 {
      font-size: 1.25rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.25rem;
    }

    .subtitle {
      font-size: 0.825rem;
      color: #64748b;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.15rem;
    }

    .form-field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .label-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .form-field label {
      font-size: 0.8rem;
      font-weight: 600;
      color: #334155;
    }

    .field-hint {
      font-size: 0.725rem;
      color: #94a3b8;
    }

    .form-field input {
      width: 100%;
      padding: 0.6rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      font-size: 0.875rem;
      color: #0f172a;
      background-color: #ffffff;
      outline: none;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }

    .form-field input:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 2px rgba(29, 78, 216, 0.12);
    }

    .btn-submit {
      margin-top: 0.5rem;
      padding: 0.65rem;
      background-color: #1e3a8a;
      color: #ffffff;
      border: 1px solid #1e3a8a;
      border-radius: 4px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
      transition: background-color 0.15s ease;
    }

    .btn-submit:hover:not(:disabled) {
      background-color: #1d4ed8;
      border-color: #1d4ed8;
    }

    .btn-submit:disabled {
      background-color: #94a3b8;
      border-color: #94a3b8;
      cursor: not-allowed;
    }

    .error-banner {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      background-color: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 4px;
      padding: 0.65rem 0.85rem;
      font-size: 0.8rem;
      color: #991b1b;
      line-height: 1.4;
    }

    .alert-icon {
      width: 16px;
      height: 16px;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .system-notice {
      margin-top: 1.75rem;
      padding-top: 1.25rem;
      border-top: 1px solid #e2e8f0;
      text-align: center;
    }

    .notice-title {
      font-size: 0.725rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin-bottom: 0.4rem;
    }

    .credential-selector {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .cred-link {
      background: none;
      border: none;
      color: #1d4ed8;
      font-size: 0.775rem;
      font-weight: 500;
      cursor: pointer;
      padding: 0;
      text-decoration: underline;
    }

    .cred-link:hover {
      color: #1e40af;
    }

    .cred-sep {
      color: #cbd5e1;
      font-size: 0.75rem;
    }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(private authService: AuthService, private router: Router) {}

  fillCredentials(u: string, p: string): void {
    this.username = u;
    this.password = p;
    this.errorMessage.set(null);
  }

  onSubmit(): void {
    if (!this.username || !this.password) {
      this.errorMessage.set('Please provide both Investigator ID and Password.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Authentication failed. Please verify credentials.');
      }
    });
  }
}
