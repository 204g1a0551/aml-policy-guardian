import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('Route Guards', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter([])
      ]
    });

    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  describe('authGuard', () => {
    it('should allow access when authenticated', () => {
      authService.token.set('valid-token');
      const route = {} as ActivatedRouteSnapshot;
      const state = { url: '/chat' } as RouterStateSnapshot;

      const result = TestBed.runInInjectionContext(() => authGuard(route, state));
      expect(result).toBe(true);
    });

    it('should redirect to /login when unauthenticated', () => {
      authService.logout();
      const navigateSpy = vi.spyOn(router, 'navigate');
      const route = {} as ActivatedRouteSnapshot;
      const state = { url: '/chat' } as RouterStateSnapshot;

      const result = TestBed.runInInjectionContext(() => authGuard(route, state));
      expect(result).toBe(false);
      expect(navigateSpy).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/chat' } });
    });
  });

  describe('roleGuard', () => {
    it('should redirect to /login when user is not logged in', () => {
      authService.currentUser.set(null);
      const navigateSpy = vi.spyOn(router, 'navigate');
      const route = { data: { expectedRoles: ['ROLE_ADMIN'] } } as unknown as ActivatedRouteSnapshot;
      const state = { url: '/documents' } as RouterStateSnapshot;

      const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
      expect(result).toBe(false);
      expect(navigateSpy).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/documents' } });
    });

    it('should allow ADMIN to access /documents and /admin/audit', () => {
      authService.currentUser.set({
        id: '1',
        username: 'admin',
        fullName: 'Admin User',
        email: 'admin@bank.internal',
        roles: ['ROLE_ADMIN']
      });

      const route = { data: { expectedRoles: ['ROLE_ADMIN'] } } as unknown as ActivatedRouteSnapshot;
      const state = { url: '/documents' } as RouterStateSnapshot;

      const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
      expect(result).toBe(true);
    });

    it('should block ANALYST from accessing ADMIN routes and redirect to /chat', () => {
      authService.currentUser.set({
        id: '2',
        username: 'analyst',
        fullName: 'Analyst User',
        email: 'analyst@bank.internal',
        roles: ['ROLE_ANALYST']
      });

      const navigateSpy = vi.spyOn(router, 'navigate');
      const route = { data: { expectedRoles: ['ROLE_ADMIN'] } } as unknown as ActivatedRouteSnapshot;
      const state = { url: '/admin/audit' } as RouterStateSnapshot;

      const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
      expect(result).toBe(false);
      expect(navigateSpy).toHaveBeenCalledWith(['/chat']);
    });
  });
});
