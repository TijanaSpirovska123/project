import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CoreService } from '../core/core.service';

@Injectable({
  providedIn: 'root',
})
export class UploadImageService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('page/upload-image', http);
  }

  uploadImage(formData: FormData, userId: string): Observable<any> {
    return this.http.post<any>(`${this.URL}?userId=${userId}`, formData);
  }
}
