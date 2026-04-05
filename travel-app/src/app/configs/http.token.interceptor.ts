import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse,
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { AuthStoreService } from '../services/core/auth-store.service';
import { AppToastrService } from '../services/core/app-toastr.service';
import { SyncAccountsStateService } from '../services/core/sync-accounts-state.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isBrowser: boolean;

  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private authStore: AuthStoreService,
    private router: Router,
    private toastr: AppToastrService,
    private syncState: SyncAccountsStateService,
  ) {
    this.isBrowser = isPlatformBrowser(this.platformId);
  }

  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    const token = this.isBrowser ? this.authStore.getAuthToken() : null;

    if (token) {
      request = request.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });
    }

    return next.handle(request).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401 && this.isBrowser) {
          const errorCode = err.error?.code;

          if (errorCode === 'META_TOKEN_EXPIRED') {
            // The app JWT is still valid — only the Meta connection expired.
            // Do NOT log the user out; just signal the UI to show reconnect prompt.
            this.syncState.markMetaTokenExpired();
            this.toastr.warning(
              'Your Meta connection has expired. Go to Sync Accounts to reconnect.',
              'Meta reconnection required'
            );
            return throwError(() => err);
          }

          // Regular 401 — app JWT expired or invalid, full logout
          this.authStore.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => err);
      })
    );
  }
}
