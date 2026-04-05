import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SyncAccountsStateService {
  private metaTokenExpired$ = new BehaviorSubject<boolean>(false);

  markMetaTokenExpired(): void {
    this.metaTokenExpired$.next(true);
  }

  isMetaTokenExpired(): Observable<boolean> {
    return this.metaTokenExpired$.asObservable();
  }

  reset(): void {
    this.metaTokenExpired$.next(false);
  }
}
