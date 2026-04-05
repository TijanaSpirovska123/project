import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { CoreService } from './core.service';


@Injectable({
  providedIn: 'root',
})
export class LoginService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('auth/login', http);
  }
}
