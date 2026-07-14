import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { environment } from '../../models/environment/environment-properties.model';

@Injectable({
  providedIn: 'root',
})
export class CoreService {
  data$: BehaviorSubject<any[]> = new BehaviorSubject<any>([]);
  subscription$: Subscription = new Subscription();

  constructor(
    @Inject(String) protected URL: string,
    protected http: HttpClient,
  ) {
    this.URL = `${environment.api}/${this.URL}`;
  }

  getAll(params?: {}) {
    return this.http?.get<any>(`${this.URL}`, { params });
  }
  getByPath(path: string): Observable<any> {
    return this.http.get<any>(`${environment.api}/${path}`);
  }

  getOneById(
    guid: string,
    responseType: 'json' | 'text' | 'blob' = 'json',
  ): Observable<any> {
    return this.http?.get<any>(`${this.URL}/${guid}`, {
      responseType: responseType as any,
    });
  }

  updateById(guid: string, data: any) {
    return this.http?.put<any>(`${this.URL}/${guid}`, data);
  }

  updateByIdWithRequestParameter(guid: string, data: any) {
    return this.http?.put<any>(`${this.URL}${guid}`, data);
  }

  create(
    data: any,
    responseType: 'json' | 'text' | 'blob' = 'json',
  ): Observable<any> {
    return this.http?.post<any>(`${this.URL}`, data, {
      responseType: responseType as any,
    });
  }

  deleteById(guid: string) {
    return this.http?.delete<any>(`${this.URL}/${guid}`);
  }

  static extractErrorMessage(
    err: any,
    fallback: string = 'An error occurred',
  ): string {
    const body = err?.error;
    const description: string | undefined = body?.description;
    const detail: string | undefined = body?.error;
    if (description && detail) return `${description}: ${detail}`;
    return description ?? detail ?? fallback;
  }
}
