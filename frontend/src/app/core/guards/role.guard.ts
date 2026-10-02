import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const expectedRoles = route.data?.['expectedRoles'] as string[] | undefined;
  const user = authService.currentUser();

  if (!user) {
    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  if (!expectedRoles || expectedRoles.length === 0) {
    return true;
  }

  const hasRole = user.roles.some(role => expectedRoles.includes(role));
  if (hasRole) {
    return true;
  }

  // Not authorized -> redirect to chat
  router.navigate(['/chat']);
  return false;
};
