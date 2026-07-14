import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { filter } from 'rxjs/operators';
import { AuthStoreService } from './auth-store.service';
import { UserConfigService } from '../user-config/user-config.service';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly THEME_KEY = 'theme';
  isDark = false;
  private isBrowser: boolean;

  constructor(
    @Inject(PLATFORM_ID) platformId: Object,
    private readonly authStore: AuthStoreService,
    private readonly userConfigService: UserConfigService,
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
    if (this.isBrowser) {
      this.isDark = localStorage.getItem(this.THEME_KEY) === 'dark';
      this.apply();
    }

    this.authStore.isLoggedIn$.pipe(filter((loggedIn) => loggedIn)).subscribe(() => {
      if (!this.isBrowser) return;
      this.userConfigService.getThemeConfig().subscribe({
        next: (config) => {
          if (config?.mode) {
            this.isDark = config.mode === 'dark';
            localStorage.setItem(this.THEME_KEY, config.mode);
            this.apply();
          }
        },
      });
    });
  }

  toggle(): void {
    this.isDark = !this.isDark;
    if (this.isBrowser) {
      const mode: 'dark' | 'light' = this.isDark ? 'dark' : 'light';
      localStorage.setItem(this.THEME_KEY, mode);
      this.apply();
      this.userConfigService.saveThemeConfig(mode).subscribe();
    }
  }

  private apply(): void {
    if (this.isBrowser) {
      document.body.classList.toggle('dark-mode', this.isDark);
      // Keep data-theme for any styles that still use it
      document.documentElement.setAttribute('data-theme', this.isDark ? 'dark' : 'light');
    }
  }
}
