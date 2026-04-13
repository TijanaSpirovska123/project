import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';
import { StoredAssetDto, StoredAssetStatusDto } from '../../models/adset/adset.model';

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

  uploadVideo(formData: FormData): Observable<any> {
    return this.http.post<any>(`${this.URL}/upload/video`, formData);
  }

  getAssetStatus(id: number): Observable<StoredAssetStatusDto> {
    return this.http.get<StoredAssetStatusDto>(`${environment.api}/assets/${id}/status`).pipe(
      map((res: any) => res?.data ?? res)
    );
  }

  list(): Observable<any> {
    return this.getAll();
  }

  get(assetId: number): Observable<any> {
    return this.getOneById(String(assetId));
  }

  getById(assetId: number): Observable<StoredAssetDto> {
    return this.http.get<any>(`${environment.api}/assets/${assetId}`).pipe(
      map((res: any) => res?.data ?? res)
    );
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
