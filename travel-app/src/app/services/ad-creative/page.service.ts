import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';

@Injectable({
  providedIn: 'root',
})
export class PageService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('page/meta', http);
  }

  getPostsByPageName(pageName: string): Observable<any> {
    return this.http.get<any>(
      `${this.URL}/${encodeURIComponent(pageName)}/posts`,
    );
  }
}
