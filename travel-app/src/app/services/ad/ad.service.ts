import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';

@Injectable({
  providedIn: 'root',
})
export class AdService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('ads', http);
  }

  syncAndGetAll(platform: string, accountId: string): Observable<any> {
    return this.getByPath(`ads/platform/${platform}/${accountId}`);
  }

  getAllByPlatform(platform: string): Observable<any> {
    return this.getByPath(`ads/${platform}`);
  }

  patchStatus(platform: string, externalId: string, status: string): Observable<any> {
    return this.http.patch(`${environment.api}/ads/external/${platform}/${externalId}`, { status });
  }
}
