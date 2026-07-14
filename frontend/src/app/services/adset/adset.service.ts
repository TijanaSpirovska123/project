import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';

@Injectable({
  providedIn: 'root',
})
export class AdSetService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('ad-sets', http);
  }

  syncAndGetAll(platform: string, accountId: string): Observable<any> {
    return this.getByPath(`ad-sets/platform/${platform}/${accountId}`);
  }

  getAllByPlatform(platform: string): Observable<any> {
    return this.getByPath(`ad-sets/${platform}`);
  }

  patchStatus(platform: string, externalId: string, status: string): Observable<any> {
    return this.http.patch(`${environment.api}/ad-sets/external/${platform}/${externalId}`, { status });
  }
}
