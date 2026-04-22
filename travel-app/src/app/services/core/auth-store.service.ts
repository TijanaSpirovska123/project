import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';
import { StorageKeys } from '../../data/core/storage-keys';

@Injectable({ providedIn: 'root' })
export class AuthStoreService {
  // Use readonly for immutable properties
  private readonly isBrowser: boolean;
  private readonly loggedInSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);

  private sessionExpired = false;

  markSessionExpired(): void {
    this.sessionExpired = true;
  }

  isSessionExpiredRedirect(): boolean {
    return this.sessionExpired;
  }

  resetSessionExpired(): void {
    this.sessionExpired = false;
  }

  // Public observable for components to subscribe to
  public readonly isLoggedIn$: Observable<boolean> =
    this.loggedInSubject.asObservable();

  constructor(@Inject(PLATFORM_ID) platformId: Object) {
    this.isBrowser = isPlatformBrowser(platformId);
    this.initializeAuthState();
  }

  // Initialize authentication state during service construction
  private initializeAuthState(): void {
    if (this.isBrowser) {
      const token = this.getAuthToken();
      this.loggedInSubject.next(!!token);
    }
  }

  // Centralized method to get items from localStorage with error handling
  private getFromStorage(key: StorageKeys): string | null {
    if (!this.isBrowser || typeof localStorage === 'undefined') {
      return null;
    }
    try {
      return localStorage.getItem(key);
    } catch (error) {
      console.error(`Error accessing localStorage for ${key}:`, error);
      return null;
    }
  }

  // Centralized method to set items in localStorage with error handling
  private setToStorage(key: StorageKeys, value: string): void {
    if (!this.isBrowser || typeof localStorage === 'undefined') {
      return;
    }
    try {
      localStorage.setItem(key, value);
    } catch (error) {
      console.error(`Error setting ${key} in localStorage:`, error);
    }
  }

  // Centralized method to clear localStorage with error handling
  private clearStorage(): void {
    if (!this.isBrowser || typeof localStorage === 'undefined') {
      return;
    }
    try {
      localStorage.clear();
    } catch (error) {
      console.error('Error clearing localStorage:', error);
    }
  }

  // Public method to get auth token
  public getAuthToken(): string | null {
    return this.getFromStorage(StorageKeys.AUTH_TOKEN);
  }

  // Public method to set logged-in state
  public setLoggedIn(state: boolean): void {
    this.loggedInSubject.next(state);
  }

  // Login method with optional parameters
  public login(token: string, userId?: string, actId?: string | null): void {
    this.resetSessionExpired();
    if (!token) {
      throw new Error('Token is required for login');
    }

    this.setToStorage(StorageKeys.AUTH_TOKEN, token);

    if (userId) {
      this.setToStorage(StorageKeys.USER_ID, userId);
    }

    if (actId) {
      this.setToStorage(StorageKeys.ACT_ID, actId);
    }

    this.setLoggedIn(true);
  }

  // Logout method
  public logout(): void {
    this.clearStorage();
    this.setLoggedIn(false);
  }

  // Method to check if user is authenticated
  public isAuthenticated(): boolean {
    return !!this.getAuthToken();
  }

  // Method to get user ID
  public getUserId(): string {
    return this.getFromStorage(StorageKeys.USER_ID) || '';
  }

  // Method to get ad account ID (strips "act_" prefix if present)
  public getActId(): string | null {
    const value = this.getFromStorage(StorageKeys.ACT_ID);
    if (!value) return null;
    return value.startsWith('act_') ? value.slice(4) : value;
  }

  // Update stored ad account ID without triggering a full re-login
  public setActId(actId: string): void {
    this.setToStorage(StorageKeys.ACT_ID, actId);
  }
}
