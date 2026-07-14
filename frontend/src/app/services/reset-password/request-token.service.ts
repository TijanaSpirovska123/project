import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { CoreService } from '../core/core.service';



@Injectable({
  providedIn: 'root',
})
export class RequestTokenService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('user/request-password-reset', http);
  }
}
