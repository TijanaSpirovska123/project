import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SyncAccountsStateService {
  private metaTokenExpired$ = new BehaviorSubject<boolean>(false);
  private metaDataChanged$ = new BehaviorSubject<number>(0);

  markMetaTokenExpired(): void {
    this.metaTokenExpired$.next(true);
  }

  isMetaTokenExpired(): Observable<boolean> {
    return this.metaTokenExpired$.asObservable();
  }

  markMetaDataChanged(): void {
    this.metaDataChanged$.next(Date.now());
  }

  onMetaDataChanged(): Observable<number> {
    return this.metaDataChanged$.asObservable();
  }

  reset(): void {
    this.metaTokenExpired$.next(false);
  }
}
