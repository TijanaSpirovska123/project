import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CoreService } from '../core/core.service';
import { InsightsSavedView } from '../../models/insights/insights-saved-view.model';

@Injectable({ providedIn: 'root' })
export class InsightsSavedViewService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('insights/saved-views', http);
  }

  createView(dto: Partial<InsightsSavedView>): Observable<InsightsSavedView> {
    return this.http.post<any>(this.URL, dto).pipe(map(r => r.data));
  }

  list(provider: string): Observable<InsightsSavedView[]> {
    const params = new HttpParams().set('provider', provider);
    return this.http.get<any>(this.URL, { params }).pipe(map(r => r.data));
  }

  getById(id: number): Observable<InsightsSavedView> {
    return this.http.get<any>(`${this.URL}/${id}`).pipe(map(r => r.data));
  }

  update(id: number, dto: Partial<InsightsSavedView>): Observable<InsightsSavedView> {
    return this.http.put<any>(`${this.URL}/${id}`, dto).pipe(map(r => r.data));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<any>(`${this.URL}/${id}`).pipe(map(() => void 0));
  }

  togglePin(id: number): Observable<InsightsSavedView> {
    return this.http.post<any>(`${this.URL}/${id}/toggle-pin`, null).pipe(map(r => r.data));
  }
}
