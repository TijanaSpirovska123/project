import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { finalize } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { OAuthService, AdAccountConnectionSummary, AdAccountConnection } from '../../services/core/oatuh.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { AdSetService } from '../../services/adset/adset.service';
import { AdService } from '../../services/ad/ad.service';
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { SyncAccountsStateService } from '../../services/core/sync-accounts-state.service';
import { PlatformCard } from '../../models/platform/platform-card.model';

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

  // Ad account selector modal
  syncModalOpen = false;
  loadingAccounts = false;
  syncing = false;
  adAccounts: AdAccountConnection[] = [];
  selectedAccount: AdAccountConnection | null = null;
  private syncingPlatform: PlatformCard | null = null;

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
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.actId = this.authStore.getActId();

    // Check if we arrived here from the OAuth callback (e.g. ?openSync=META)
    const openSyncKey = this.route.snapshot.queryParamMap.get('openSync')?.toUpperCase() ?? null;

    this.loadConnectionStatus(() => {
      if (openSyncKey) {
        // Clear the query param so a refresh doesn't re-open the modal
        this.router.navigate([], { replaceUrl: true, queryParams: {} });
        const platform = this.platforms.find(p => p.key === openSyncKey);
        if (platform) this.openSyncModal(platform);
      }
    });

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

  loadConnectionStatus(onDone?: () => void): void {
    this.isLoadingConnections = true;
    this.oauthService.getConnections().pipe(
      finalize(() => {
        this.isLoadingConnections = false;
        this.cdr.detectChanges();
        onDone?.();
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
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Meta connection failed'), 'Error');
          }
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
    if (platform.key === 'META') this.openSyncModal(platform);
  }

  isConnected(key: string): boolean {
    return this.platforms.find(p => p.key === key)?.connected ?? false;
  }

  // ── Ad account selector modal ──────────────────────────────────────────────

  openSyncModal(platform: PlatformCard): void {
    this.syncingPlatform = platform;
    this.syncModalOpen = true;
    this.selectedAccount = null;
    this.adAccounts = [];
    this.loadingAccounts = true;
    this.cdr.detectChanges();

    this.oauthService.getConnectedAdAccounts(platform.key).subscribe({
      next: (accounts) => {
        this.adAccounts = accounts;
        this.loadingAccounts = false;
        if (accounts.length === 1) this.selectedAccount = accounts[0];
        this.cdr.detectChanges();
      },
      error: () => {
        this.loadingAccounts = false;
        this.cdr.detectChanges();
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error('Failed to load ad accounts');
        }
      },
    });
  }

  selectAccount(account: AdAccountConnection): void {
    this.selectedAccount = account;
  }

  closeSyncModal(): void {
    if (this.syncing) return;
    this.syncModalOpen = false;
    this.selectedAccount = null;
    this.adAccounts = [];
    this.syncingPlatform = null;
  }

  confirmSync(): void {
    if (!this.selectedAccount || this.syncing || !this.syncingPlatform) return;
    const platform = this.syncingPlatform;
    const accountId = this.selectedAccount.adAccountId;
    const accountName = this.selectedAccount.adAccountName;
    this.syncing = true;
    this.cdr.detectChanges();

    this.syncMetaWithAccount(platform, accountId, accountName, () => {
      this.syncing = false;
      this.closeSyncModal();
      this.cdr.detectChanges();
    }, () => {
      this.syncing = false;
      this.cdr.detectChanges();
    });
  }

  private syncMetaWithAccount(
    platform: PlatformCard,
    accountId: string,
    accountName: string,
    onSuccess: () => void,
    onError: () => void,
  ): void {
    platform.syncing = true;
    this.syncStep = 'Syncing campaigns… (1/3)';
    this.syncStepNum = 1;
    this.cdr.detectChanges();

    this.campaignService.syncAndGetAll('META', accountId).subscribe({
      next: (campaignsRes: any) => {
        const campaigns = campaignsRes?.data || [];
        this.syncStep = 'Syncing ad sets… (2/3)';
        this.syncStepNum = 2;
        this.cdr.detectChanges();

        this.adSetService.syncAndGetAll('META', accountId).subscribe({
          next: (adSetsRes: any) => {
            const adSets = adSetsRes?.data || [];
            this.syncStep = 'Syncing ads… (3/3)';
            this.syncStepNum = 3;
            this.cdr.detectChanges();

            this.adService.syncAndGetAll('META', accountId).subscribe({
              next: (adsRes: any) => {
                const ads = adsRes?.data || [];
                platform.syncing = false;
                platform.lastSynced = new Date().toISOString();
                this.syncStep = '';
                this.syncStepNum = 0;
                this.cdr.detectChanges();
                this.toastr.success(
                  `Synced from ${accountName} — ${campaigns.length} campaigns, ${adSets.length} ad sets, ${ads.length} ads`,
                  'Sync Complete'
                );
                onSuccess();
              },
              error: (err: any) => {
                platform.syncing = false; this.syncStep = ''; this.syncStepNum = 0;
                this.cdr.detectChanges();
                if (!this.authStore.isSessionExpiredRedirect()) {
                  this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync ads'));
                }
                onError();
              },
            });
          },
          error: (err: any) => {
            platform.syncing = false; this.syncStep = ''; this.syncStepNum = 0;
            this.cdr.detectChanges();
            if (!this.authStore.isSessionExpiredRedirect()) {
              this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync ad sets'));
            }
            onError();
          },
        });
      },
      error: (err: any) => {
        platform.syncing = false; this.syncStep = ''; this.syncStepNum = 0;
        this.cdr.detectChanges();
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync campaigns'));
        }
        onError();
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
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Disconnect failed'), 'Error');
          }
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
