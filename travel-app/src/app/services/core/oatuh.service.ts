import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { CoreService } from './core.service';
import { Observable } from 'rxjs';
import { OAuthConnectResponse } from '../../models/oauth/oauth.model';

export interface AdAccountConnectionSummary {
  provider: 'META' | 'TIKTOK' | 'PINTEREST' | 'GOOGLE' | 'LINKEDIN' | 'REDDIT';
  connected: boolean;
  lastSynced: string | null;
  tokenStatus: 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | null;
}

@Injectable({ providedIn: 'root' })
export class OAuthService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('oauth', http);
  }

  connectMeta(): Observable<OAuthConnectResponse> {
    return this.http.post<OAuthConnectResponse>(
      `${this.URL}/meta/connect`,
      {},
      { withCredentials: true }
    );
  }

  disconnectMeta(): Observable<void> {
    return this.http.delete<void>(
      `${this.URL}/meta/disconnect`,
      { withCredentials: true }
    );
  }

  getConnections(): Observable<AdAccountConnectionSummary[]> {
    return this.getByPath('ad-account-connections');
  }
}
