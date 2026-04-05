import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';

@Injectable({
  providedIn: 'root',
})
export class AssetLibraryService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('assets', http);
  }

  upload(file: File): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<any>(`${this.URL}/upload`, form);
  }

  list(): Observable<any> {
    return this.getAll();
  }

  get(assetId: number): Observable<any> {
    return this.getOneById(String(assetId));
  }

  // Used only for <a href> download triggers — NOT for <img src>
  getVariantDownloadUrl(assetId: number, variantKey: string): string {
    return `${environment.api}/assets/${assetId}/variants/${variantKey}/download`;
  }

  // Fetches variant via HttpClient (sends Bearer token), returns a blob object URL
  fetchVariantBlob(assetId: number, variantKey: string): Observable<string> {
    return this.http
      .get(`${environment.api}/assets/${assetId}/variants/${variantKey}/download`, {
        responseType: 'blob',
      })
      .pipe(map(blob => URL.createObjectURL(blob)));
  }
}
