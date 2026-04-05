import { HttpClient } from '@angular/common/http';
import { Injectable, Inject } from '@angular/core';
import { CoreService } from './core.service';
import { Observable } from 'rxjs';
import { environment } from '../../models/environment/environment-properties.model';


@Injectable({
  providedIn: 'root',
})
export class LogoutService extends CoreService {
  constructor(@Inject(HttpClient) http: HttpClient) {
    super('auth/logout', http);
  }

  // logOut():Observable<void> {
  //   return this.http.post(`${this.URL}`, {}, { withCredentials: true });
  // }

  logout() {
    return this.http.post(
      `${environment.api}/auth/logout`,
      {},
      { withCredentials: true }
    );
  }
}
