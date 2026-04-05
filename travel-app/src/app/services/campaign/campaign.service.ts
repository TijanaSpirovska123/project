import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';

@Injectable({
  providedIn: 'root',
})
export class CampaignService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('campaigns', http);
  }

  syncAndGetAll(platform: string, accountId: string): Observable<any> {
    return this.getByPath(`campaigns/platform/${platform}/${accountId}`);
  }

  getAllByPlatform(platform: string): Observable<any> {
    return this.getByPath(`campaigns/${platform}`);
  }

  getAdSetsByCampaign(platform: string, campaignId: string): Observable<any> {
    return this.getByPath(
      `campaigns/platform/${platform}/campaign/${campaignId}/adsets`,
    );
  }

  patchStatus(
    platform: string,
    externalId: string,
    status: string,
  ): Observable<any> {
    return this.http.patch(
      `${environment.api}/campaigns/external/${platform}/${externalId}`,
      { status },
    );
  }
}
