import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
} from '@angular/common/http';
import { Observable } from 'rxjs';

export const REQUEST_ID_HEADER = 'X-Request-Id';

function generateRequestId(): string {
  if (
    typeof crypto !== 'undefined' &&
    typeof crypto.randomUUID === 'function'
  ) {
    return crypto.randomUUID();
  }

  // SSR / older-runtime fallback — still unique enough to correlate one
  // request across nginx, backend, and ai-service logs.
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Tags every outgoing HTTP call with a correlation id so a failure shown
 * in the browser (e.g. a 502 from /api/insights) can be traced to the
 * exact backend and ai-service log lines that produced it.
 */
@Injectable()
export class RequestIdInterceptor implements HttpInterceptor {
  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    const requestWithId = request.clone({
      setHeaders: { [REQUEST_ID_HEADER]: generateRequestId() },
    });

    return next.handle(requestWithId);
  }
}
