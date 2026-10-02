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
    <div class="login-container">
      <div class="login-card">
        <div class="brand-header">
          <div class="badge">AML Compliance Shield</div>
          <h2>Policy & Investigation Assistant</h2>
          <p>Sign in with your bank security credentials to access approved AML guidelines and investigation assistant.</p>
        </div>

        <form (ngSubmit)="onSubmit()">
          <div class="form-group">
            <label for="username">Username</label>
            <input 
              id="username" 
              name="username" 
              type="text" 
              [(ngModel)]="username" 
              required 
              placeholder="e.g. analyst or admin"
            />
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <input 
              id="password" 
              name="password" 
              type="password" 
              [(ngModel)]="password" 
              required 
              placeholder="Enter secure password"
            />
          </div>

          @if (errorMessage()) {
            <div class="alert alert-error">
              {{ errorMessage() }}
            </div>
          }

          <button type="submit" [disabled]="isLoading()" class="btn-primary">
            @if (isLoading()) {
              <span>Authenticating...</span>
            } @else {
              <span>Secure Sign In</span>
            }
          </button>
        </form>

        <div class="demo-hints">
          <p><strong>Demo Test Accounts:</strong></p>
          <div class="demo-buttons">
            <button type="button" class="btn-chip" (click)="fillCredentials('analyst', 'AdminPass123!')">
              FIU Analyst (analyst)
            </button>
            <button type="button" class="btn-chip" (click)="fillCredentials('admin', 'AdminPass123!')">
              Compliance Admin (admin)
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 80vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .login-card {
      width: 100%;
      max-width: 440px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 2.25rem;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05);
    }
    .brand-header h2 {
      font-size: 1.5rem;
      color: #0f172a;
      margin: 0.5rem 0 0.25rem 0;
    }
    .brand-header p {
      color: #64748b;
      font-size: 0.875rem;
      margin-bottom: 1.5rem;
    }
    .badge {
      display: inline-block;
      background: #dbeafe;
      color: #1e40af;
      padding: 0.2rem 0.6rem;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .form-group {
      margin-bottom: 1.25rem;
    }
    .form-group label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      color: #334155;
      margin-bottom: 0.35rem;
    }
    .form-group input {
      width: 100%;
      padding: 0.65rem 0.85rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.95rem;
      box-sizing: border-box;
      outline: none;
      transition: border-color 0.15s;
    }
    .form-group input:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
    }
    .btn-primary {
      width: 100%;
      padding: 0.75rem;
      background: #1e3a8a;
      color: #ffffff;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      font-size: 0.95rem;
      transition: background 0.15s;
    }
    .btn-primary:hover:not(:disabled) {
      background: #172554;
    }
    .alert-error {
      background: #fee2e2;
      color: #991b1b;
      padding: 0.65rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
      margin-bottom: 1rem;
    }
    .demo-hints {
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px dashed #e2e8f0;
      font-size: 0.8rem;
      color: #64748b;
    }
    .demo-buttons {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .btn-chip {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 0.3rem 0.6rem;
      font-size: 0.75rem;
      cursor: pointer;
      color: #334155;
    }
    .btn-chip:hover {
      background: #e2e8f0;
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
      this.errorMessage.set('Please enter both username and password.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/investigation']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Authentication failed. Please verify credentials.');
      }
    });
  }
}
