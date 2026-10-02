import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const expectedRoles = route.data?.['expectedRoles'] as string[] | undefined;
  const user = authService.currentUser();

  if (!user) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  if (!expectedRoles || expectedRoles.length === 0) {
    return true;
  }

  const hasRole = user.roles.some(role => expectedRoles.includes(role));
  if (hasRole) {
    return true;
  }

  return router.createUrlTree(['/chat']);
};
