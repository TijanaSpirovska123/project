import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';
import { Provider } from '../../data/provider/provider.enum';
import {
  InsightResponse,
  InsightSyncRequest,
} from '../../models/insights/insight.model';
import { InsightsBreakdownRow } from '../../components/insights/insights-breakdown.types';

@Injectable({
  providedIn: 'root',
})
export class InsightsService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('insights', http);
  }

  queryCampaigns(
    provider: Provider,
    adAccountId: string,
    dateStart: string,
    dateStop: string,
    ids?: string[],
  ): Observable<InsightResponse> {
    let params = new HttpParams()
      .set('provider', provider)
      .set('adAccountId', adAccountId)
      .set('dateStart', dateStart)
      .set('dateStop', dateStop);
    if (ids && ids.length === 1) {
      params = params.set('campaignId', ids[0]);
    } else if (ids && ids.length > 1) {
      ids.forEach((id) => {
        params = params.append('campaignId', id);
      });
    }
    return this.http.get<InsightResponse>(`${this.URL}/campaigns`, { params });
  }

  queryAdSets(
    provider: Provider,
    adAccountId: string,
    dateStart: string,
    dateStop: string,
    ids?: string[],
  ): Observable<InsightResponse> {
    let params = new HttpParams()
      .set('provider', provider)
      .set('adAccountId', adAccountId)
      .set('dateStart', dateStart)
      .set('dateStop', dateStop);
    if (ids && ids.length === 1) {
      params = params.set('adsetId', ids[0]);
    } else if (ids && ids.length > 1) {
      ids.forEach((id) => {
        params = params.append('adsetId', id);
      });
    }
    return this.http.get<InsightResponse>(`${this.URL}/adsets`, { params });
  }

  queryAds(
    provider: Provider,
    adAccountId: string,
    dateStart: string,
    dateStop: string,
    ids?: string[],
  ): Observable<InsightResponse> {
    let params = new HttpParams()
      .set('provider', provider)
      .set('adAccountId', adAccountId)
      .set('dateStart', dateStart)
      .set('dateStop', dateStop);
    if (ids && ids.length === 1) {
      params = params.set('adId', ids[0]);
    } else if (ids && ids.length > 1) {
      ids.forEach((id) => {
        params = params.append('adId', id);
      });
    }
    return this.http.get<InsightResponse>(`${this.URL}/ads`, { params });
  }

  syncCampaignsBatch(body: InsightSyncRequest): Observable<InsightResponse> {
    return this.http.post<InsightResponse>(
      `${this.URL}/sync/campaigns/batch`,
      body,
    );
  }

  syncAdSetsBatch(body: InsightSyncRequest): Observable<InsightResponse> {
    return this.http.post<InsightResponse>(
      `${this.URL}/sync/adsets/batch`,
      body,
    );
  }

  syncAdsBatch(body: InsightSyncRequest): Observable<InsightResponse> {
    return this.http.post<InsightResponse>(`${this.URL}/sync/ads/batch`, body);
  }

  syncAccount(
    adAccountId: string,
    body: InsightSyncRequest,
  ): Observable<InsightResponse> {
    return this.http.post<InsightResponse>(
      `${this.URL}/sync/account/${adAccountId}`,
      body,
    );
  }

  getMetricNames(): Observable<{ data: string[]; success: boolean }> {
    return this.http.get<{ data: string[]; success: boolean }>(
      `${this.URL}/metrics`,
    );
  }

  getBreakdown(
    provider: string,
    adAccountId: string,
    dimension: string,
    dateStart: string,
    dateStop: string,
  ): Observable<{ data: InsightsBreakdownRow[]; success: boolean }> {
    const params = new HttpParams()
      .set('provider', provider)
      .set('adAccountId', adAccountId)
      .set('dimension', dimension)
      .set('dateStart', dateStart)
      .set('dateStop', dateStop);
    return this.http.get<{ data: InsightsBreakdownRow[]; success: boolean }>(
      `${this.URL}/breakdown`,
      { params },
    );
  }
}
