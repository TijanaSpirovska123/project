import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CoreService } from '../core/core.service';
import { environment } from '../../models/environment/environment-properties.model';
import {
  StoredAssetDto,
  AdAssetDto,
  CreativeDto,
} from '../../models/adset/adset.model';

@Injectable({
  providedIn: 'root',
})
export class CreativeService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('page/creative', http);
  }

  createCreative(
    postId: string,
    userId: string,
    pageName: string,
    creativeName: string,
  ): Observable<any> {
    const requestBody = {
      name: creativeName,
    };
    return this.http.post<any>(
      `${this.URL}/${postId}?userId=${userId}&pageName=${pageName}`,
      requestBody,
    );
  }

  /** Create a Meta ad creative from a Facebook page post */
  createCreativeFromPost(
    postId: string,
    platform: string,
    pageName: string,
    body: { name?: string; linkUrl?: string },
  ): Observable<any> {
    const params: any = { pageName };
    if (body.linkUrl) params.linkUrl = body.linkUrl;
    return this.http.post<any>(
      `${this.URL}/${postId}/${platform}`,
      { name: body.name },
      { params },
    );
  }

  getAllCreatives(userId: string, adAccountId: string): Observable<any> {
    return this.getByPath(
      `page/creatives/all-with-details?userId=${userId}&adAccountId=${adAccountId}`,
    );
  }

  uploadAdImageFromStoredAsset(
    adAccountId: string,
    assetId: number,
    variantKey: string,
    pageName?: string,
  ): Observable<any> {
    const params: any = { adAccountId, assetId, variantKey };
    if (pageName) params.pageName = pageName;
    return this.http.post(
      `${environment.api}/page/upload-image/from-stored-asset`,
      null,
      { params },
    );
  }

  createCreativeFromStoredAsset(
    storedAssetId: number,
    variantKey: string,
    platform: string,
    adAccountId: string,
    body: { name?: string; linkUrl?: string },
  ): Observable<any> {
    const params: any = { storedAssetId, variantKey, platform, adAccountId };
    return this.http.post(`${this.URL}/from-stored-asset`, body, { params });
  }

  /** List stored assets from the asset-creative service */
  listAssets(): Observable<StoredAssetDto[]> {
    return this.http.get<any>(`${environment.api}/page/asset-creative/assets`);
  }

  /** Upload a new image asset to the asset-creative service */
  uploadAsset(file: File): Observable<StoredAssetDto> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<any>(
      `${environment.api}/page/asset-creative/assets/upload`,
      form,
    );
  }

  /** Push a stored asset image to Meta ad account */
  pushAssetToMeta(
    assetId: number,
    adAccountId: string,
    variantKey: string,
    pageName?: string,
  ): Observable<AdAssetDto> {
    const params: any = { adAccountId, variantKey };
    if (pageName) params.pageName = pageName;
    return this.http.post<any>(
      `${environment.api}/page/asset-creative/assets/${assetId}/push-to-meta`,
      null,
      { params },
    );
  }

  /** Save creative as draft in DB from a stored asset */
  createCreativeFromAsset(
    params: {
      storedAssetId: number;
      variantKey: string;
      platform: string;
      adAccountId: string;
    },
    body: {
      pageId: string;
      objectUrl: string;
      imageHash: string;
      message: string;
      headline: string;
      urlTags: string;
      objectType: string;
    },
  ): Observable<CreativeDto> {
    return this.http.post<any>(
      `${environment.api}/page/creative/from-stored-asset`,
      body,
      { params },
    );
  }

  /** Post creative directly to the platform (e.g. Meta) from a stored asset */
  publishCreativeFromAsset(
    adAccountId: string,
    platform: string,
    body: {
      storedAssetId: number;
      variantKey: string;
      pageId: string;
      objectUrl: string;
      imageHash: string;
      message: string;
      headline: string;
      urlTags: string;
      objectType: string;
    },
  ): Observable<CreativeDto> {
    const params: any = { adAccountId, platform };
    return this.http.post<any>(
      `${environment.api}/page/creative/from-asset`,
      body,
      { params },
    );
  }

  /** Fetch a stored asset-creative variant as an authenticated blob object URL */
  fetchAssetCreativeThumbnailBlob(
    assetId: number,
    variantKey: string,
  ): Observable<string> {
    return this.http
      .get(
        `${environment.api}/page/asset-creative/assets/${assetId}/variants/${variantKey}/download`,
        {
          responseType: 'blob',
        },
      )
      .pipe(map((blob: Blob) => URL.createObjectURL(blob)));
  }

  /** Fetch all asset-creative assets (with hash) */
  getAssetCreativeAssets(): Observable<any> {
    return this.http.get<any>(`${environment.api}/page/asset-creative/assets`);
  }

  /** Create creative using hash-based approach */
  createCreativeWithHash(body: {
    pageId: string;
    objectUrl: string;
    imageHash: string;
    variantId: number;
    message: string;
    headline: string;
    urlTags?: string;
    objectType?: string;
  }): Observable<any> {
    return this.http.post<any>(
      `${environment.api}/page/asset-creative/creatives`,
      body,
    );
  }
}
