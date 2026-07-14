import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { isPlatformBrowser } from '@angular/common';
import { AuthStoreService } from '../services/core/auth-store.service';

export const authGuard: CanActivateFn = (_route, state: RouterStateSnapshot) => {
  const auth = inject(AuthStoreService);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  // On server-side rendering, allow navigation (auth will be checked on browser)
  if (!isPlatformBrowser(platformId)) {
    return true;
  }

  // On browser, check authentication
  if (auth.isAuthenticated()) return true;

  // Save the attempted URL so login can redirect back after success
  try {
    sessionStorage.setItem('redirectAfterLogin', state.url);
  } catch { /* ignore storage errors */ }

  return router.createUrlTree(['/login']);
};
