import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';
import {
  ColumnConfigRequest,
  ColumnConfigResponse,
  ThemeConfigRequest,
  ThemeConfigResponse,
} from '../../models/user-config/user-config.model';

@Injectable({ providedIn: 'root' })
export class UserConfigService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('users', http);
  }

  getColumnConfig(
    entityType: 'CAMPAIGN' | 'AD_SET' | 'AD',
  ): Observable<ColumnConfigResponse> {
    const params = new HttpParams().set('entityType', entityType);
    return this.http
      .get<{ data: ColumnConfigResponse }>(`${environment.api}/users/column-config`, { params })
      .pipe(map((res) => res.data));
  }

  saveColumnConfig(
    entityType: 'CAMPAIGN' | 'AD_SET' | 'AD',
    columns: string[],
  ): Observable<void> {
    const body: ColumnConfigRequest = { entityType, columns };
    return this.http.patch<void>(
      `${environment.api}/users/column-config`,
      body,
    );
  }

  getThemeConfig(): Observable<ThemeConfigResponse | null> {
    return this.http
      .get<{ data: ThemeConfigResponse | null }>(`${environment.api}/users/theme-config`)
      .pipe(map((res) => res.data));
  }

  saveThemeConfig(mode: 'dark' | 'light'): Observable<void> {
    const body: ThemeConfigRequest = { mode };
    return this.http.patch<void>(`${environment.api}/users/theme-config`, body);
  }
}
