import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';
import { PixelDto } from '../../models/ad-creative/pixel.model';

@Injectable({
  providedIn: 'root',
})
export class PixelService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('pixels', http);
  }

  getAllFromMeta(adAccountId: string): Observable<PixelDto[]> {
    return this.getByPath(`pixels/meta/${encodeURIComponent(adAccountId)}`);
  }
}
