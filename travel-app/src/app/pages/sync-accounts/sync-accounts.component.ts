import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { finalize } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { OAuthService, AdAccountConnectionSummary } from '../../services/core/oatuh.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { AdSetService } from '../../services/adset/adset.service';
import { AdService } from '../../services/ad/ad.service';
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { SyncAccountsStateService } from '../../services/core/sync-accounts-state.service';

interface PlatformCard {
  key: string;
  name: string;
  icon: string;
  color: string;
  enabled: boolean;
  syncing: boolean;
  connected: boolean;
  lastSynced: string | null;
  tokenStatus: 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | null;
}

@Component({
  selector: 'app-sync-accounts',
  standalone: false,
  templateUrl: './sync-accounts.component.html',
  styleUrls: ['./sync-accounts.component.scss'],
})
export class SyncAccountsComponent implements OnInit, OnDestroy {
  platforms: PlatformCard[] = [
    { key: 'META',      name: 'Meta',      icon: 'facebook',    color: '#1877f2', enabled: true,  syncing: false, connected: false, lastSynced: null, tokenStatus: null },
    { key: 'TIKTOK',    name: 'TikTok',    icon: 'music_video', color: '#000000', enabled: false, syncing: false, connected: false, lastSynced: null, tokenStatus: null },
    { key: 'PINTEREST', name: 'Pinterest', icon: 'push_pin',    color: '#e60023', enabled: false, syncing: false, connected: false, lastSynced: null, tokenStatus: null },
    { key: 'GOOGLE',    name: 'Google Ads',icon: 'ads_click',   color: '#4285f4', enabled: false, syncing: false, connected: false, lastSynced: null, tokenStatus: null },
  ];

  actId: string | null = null;
  isConnecting = false;
  isLoadingConnections = false;
  syncStep = '';
  syncStepNum = 0;

  showDisconnectDialog = false;
  disconnectPlatform: PlatformCard | null = null;
  isDisconnecting = false;

  private syncStateSub?: Subscription;

  constructor(
    private readonly oauthService: OAuthService,
    private readonly campaignService: CampaignService,
    private readonly adSetService: AdSetService,
    private readonly adService: AdService,
    private readonly authStore: AuthStoreService,
    private readonly toastr: AppToastrService,
    private readonly cdr: ChangeDetectorRef,
    private readonly syncState: SyncAccountsStateService,
  ) {}

  ngOnInit(): void {
    this.actId = this.authStore.getActId();
    this.loadConnectionStatus();

    // Refresh the card UI if the interceptor signals a Meta token expiry at runtime
    this.syncStateSub = this.syncState.isMetaTokenExpired().subscribe(expired => {
      if (expired) {
        this.loadConnectionStatus();
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy(): void {
    this.syncStateSub?.unsubscribe();
  }

  isTokenExpired(platform: PlatformCard): boolean {
    return platform.tokenStatus === 'EXPIRED';
  }

  isTokenExpiringSoon(platform: PlatformCard): boolean {
    return platform.tokenStatus === 'EXPIRING_SOON';
  }

  getSyncTooltip(platform: PlatformCard): string {
    if (!platform.connected) return 'Connect your account first';
    if (this.isTokenExpired(platform)) return 'Reconnect your account — token has expired';
    return '';
  }

  loadConnectionStatus(): void {
    this.isLoadingConnections = true;
    this.oauthService.getConnections().pipe(
      finalize(() => {
        this.isLoadingConnections = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (connections: AdAccountConnectionSummary[]) => {
        connections.forEach(conn => {
          const card = this.platforms.find(p => p.key === conn.provider);
          if (card) {
            card.connected = conn.connected;
            card.lastSynced = conn.lastSynced;
            card.tokenStatus = conn.tokenStatus;
          }
        });

        // If Meta is now VALID after previously being marked expired, clear the signal
        const meta = connections.find(c => c.provider === 'META');
        if (meta?.connected && meta.tokenStatus === 'VALID') {
          this.syncState.reset();
        }
      },
      error: () => {
        // API not yet available — fall back to actId presence for Meta
        const metaCard = this.platforms.find(p => p.key === 'META');
        if (metaCard) metaCard.connected = !!this.actId;
      },
    });
  }

  connectPlatform(platform: PlatformCard): void {
    if (!platform.enabled) {
      this.toastr.info(`${platform.name} integration coming soon!`);
      return;
    }
    if (platform.key === 'META') {
      this.isConnecting = true;
      this.oauthService.connectMeta().subscribe({
        next: (res) => {
          window.location.href = res.authorizationUrl;
        },
        error: (err) => {
          this.isConnecting = false;
          this.toastr.error(CoreService.extractErrorMessage(err, 'Meta connection failed'), 'Error');
        },
      });
    }
  }

  syncPlatform(platform: PlatformCard): void {
    if (!platform.enabled || !platform.connected) return;
    if (this.isTokenExpired(platform)) {
      this.toastr.warning('Meta token has expired. Please reconnect your account first.');
      return;
    }
    if (!this.actId) {
      this.toastr.warning('No Meta ad account linked. Please connect first.');
      return;
    }
    if (platform.key === 'META') this.syncMeta(platform);
  }

  private syncMeta(platform: PlatformCard): void {
    platform.syncing = true;
    this.syncStep = 'Syncing campaigns… (1/3)';
    this.syncStepNum = 1;
    this.cdr.detectChanges();

    this.campaignService.syncAndGetAll('META', `act_${this.actId}`).subscribe({
      next: (campaignsRes: any) => {
        const campaigns = campaignsRes?.data || [];
        this.syncStep = 'Syncing ad sets… (2/3)';
        this.syncStepNum = 2;
        this.cdr.detectChanges();

        this.adSetService.syncAndGetAll('META', `act_${this.actId}`).subscribe({
          next: (adSetsRes: any) => {
            const adSets = adSetsRes?.data || [];
            this.syncStep = 'Syncing ads… (3/3)';
            this.syncStepNum = 3;
            this.cdr.detectChanges();

            this.adService.syncAndGetAll('META', `act_${this.actId}`).subscribe({
              next: (adsRes: any) => {
                const ads = adsRes?.data || [];
                platform.syncing = false;
                platform.lastSynced = new Date().toISOString();
                this.syncStep = '';
                this.syncStepNum = 0;
                this.cdr.detectChanges();
                this.toastr.success(
                  `Sync complete — ${campaigns.length} campaigns, ${adSets.length} ad sets, ${ads.length} ads`,
                  'Sync Complete'
                );
              },
              error: (err: any) => {
                platform.syncing = false; this.syncStep = ''; this.syncStepNum = 0;
                this.cdr.detectChanges();
                this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync ads'));
              },
            });
          },
          error: (err: any) => {
            platform.syncing = false; this.syncStep = ''; this.syncStepNum = 0;
            this.cdr.detectChanges();
            this.toastr.warning('Ad sets sync failed');
          },
        });
      },
      error: (err: any) => {
        platform.syncing = false; this.syncStep = ''; this.syncStepNum = 0;
        this.cdr.detectChanges();
        this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync campaigns'));
      },
    });
  }

  openDisconnectDialog(platform: PlatformCard): void {
    this.disconnectPlatform = platform;
    this.showDisconnectDialog = true;
  }

  cancelDisconnect(): void {
    this.showDisconnectDialog = false;
    this.disconnectPlatform = null;
  }

  confirmDisconnect(): void {
    if (!this.disconnectPlatform || this.isDisconnecting) return;
    const platform = this.disconnectPlatform;

    if (platform.key === 'META') {
      this.isDisconnecting = true;
      this.cdr.detectChanges();

      this.oauthService.disconnectMeta().pipe(
        finalize(() => {
          this.isDisconnecting = false;
          this.cdr.detectChanges();
        })
      ).subscribe({
        next: () => {
          platform.connected = false;
          platform.lastSynced = null;
          this.showDisconnectDialog = false;
          this.disconnectPlatform = null;
          this.toastr.info('Account disconnected. Your synced data remains in the app.');
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Disconnect failed'), 'Error');
        },
      });
    }
  }

  formatLastSynced(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHrs = Math.floor(diffMins / 60);
    if (diffHrs < 24) return `${diffHrs}h ago`;
    return d.toLocaleDateString();
  }
}
