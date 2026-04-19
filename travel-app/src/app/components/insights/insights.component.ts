import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectorRef,
  ViewChild,
  ElementRef,
} from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { finalize } from 'rxjs/operators';
import { Chart, registerables } from 'chart.js';
import { AppToastrService } from '../../services/core/app-toastr.service';

Chart.register(...registerables);
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { InsightsService } from '../../services/insights/insights.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { AdSetService } from '../../services/adset/adset.service';
import { AdService } from '../../services/ad/ad.service';
import { OAuthService, AdAccountConnectionSummary } from '../../services/core/oatuh.service';
import { Provider } from '../../data/provider/provider.enum';
import { Campaign } from '../../models/campaign/campaign';
import { AdSetResponse, AdResponse } from '../../models/adset/adset.model';
import {
  InsightSnapshot,
  MetricBlock,
  MetricConfig,
  ALL_INSIGHT_METRICS,
  DEFAULT_METRICS,
  METRIC_ICONS,
  METRIC_CONFIG,
} from '../../models/insights/insight.model';
import { DateRange } from '../shared/date-range-picker.component';

const CHART_COLORS = [
  '#1ca698',
  '#3498db',
  '#9b59b6',
  '#f39c12',
  '#e74c3c',
  '#2ecc71',
];

const CAMPAIGN_FIELDS = [
  // Core
  'impressions', 'reach', 'frequency', 'clicks', 'unique_clicks',
  'spend', 'cpm', 'cpc', 'ctr', 'unique_ctr', 'cpp', 'cost_per_unique_click', 'social_spend',
  // Engagement
  'inline_link_clicks', 'inline_post_engagement', 'unique_inline_link_clicks',
  'inline_link_click_ctr', 'outbound_clicks', 'unique_outbound_clicks', 'website_ctr',
  // Video
  'video_play_actions', 'video_thruplay_watched_actions', 'video_avg_time_watched_actions',
  'video_p25_watched_actions', 'video_p50_watched_actions', 'video_p75_watched_actions',
  'video_p95_watched_actions', 'video_p100_watched_actions', 'video_30_sec_watched_actions',
  'video_continuous_2_sec_watched_actions',
  // Conversions / Actions
  'actions', 'unique_actions', 'action_values',
  'cost_per_action_type', 'cost_per_unique_action_type',
  'conversions', 'conversion_values', 'purchase_roas', 'cost_per_conversion',
  // Estimated
  'estimated_ad_recall_rate', 'estimated_ad_recallers',
  // Date / Account
  'account_currency', 'date_start', 'date_stop',
];

const ADSET_FIELDS = [
  // Core
  'impressions', 'reach', 'frequency', 'clicks', 'unique_clicks',
  'spend', 'cpm', 'cpc', 'ctr', 'unique_ctr', 'cpp', 'cost_per_unique_click', 'social_spend',
  // Engagement
  'inline_link_clicks', 'inline_post_engagement', 'unique_inline_link_clicks',
  'inline_link_click_ctr', 'outbound_clicks', 'unique_outbound_clicks', 'website_ctr',
  // Video
  'video_play_actions', 'video_thruplay_watched_actions', 'video_avg_time_watched_actions',
  'video_p25_watched_actions', 'video_p50_watched_actions', 'video_p75_watched_actions',
  'video_p95_watched_actions', 'video_p100_watched_actions', 'video_30_sec_watched_actions',
  'video_continuous_2_sec_watched_actions',
  // Conversions / Actions
  'actions', 'unique_actions', 'action_values',
  'cost_per_action_type', 'cost_per_unique_action_type',
  'conversions', 'conversion_values', 'purchase_roas', 'cost_per_conversion',
  // Estimated
  'estimated_ad_recall_rate', 'estimated_ad_recallers',
  // Date
  'date_start', 'date_stop',
];

const AD_FIELDS = [
  // Core
  'impressions', 'reach', 'frequency', 'clicks', 'unique_clicks',
  'spend', 'cpm', 'cpc', 'ctr', 'unique_ctr', 'cpp', 'cost_per_unique_click', 'social_spend',
  // Engagement
  'inline_link_clicks', 'inline_post_engagement', 'unique_inline_link_clicks',
  'inline_link_click_ctr', 'outbound_clicks', 'unique_outbound_clicks', 'website_ctr',
  // Video
  'video_play_actions', 'video_thruplay_watched_actions', 'video_avg_time_watched_actions',
  'video_p25_watched_actions', 'video_p50_watched_actions', 'video_p75_watched_actions',
  'video_p95_watched_actions', 'video_p100_watched_actions', 'video_30_sec_watched_actions',
  'video_continuous_2_sec_watched_actions',
  // Conversions / Actions
  'actions', 'unique_actions', 'action_values',
  'cost_per_action_type', 'cost_per_unique_action_type',
  'conversions', 'conversion_values', 'purchase_roas', 'cost_per_conversion',
  // Estimated
  'estimated_ad_recall_rate', 'estimated_ad_recallers',
  // Date
  'date_start', 'date_stop',
];

@Component({
  selector: 'app-insights',
  standalone: false,
  templateUrl: './insights.component.html',
  styleUrls: ['./insights.component.scss'],
})
export class InsightsComponent implements OnInit, OnDestroy {
  @ViewChild('insightGraph') graphCanvasRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('spendChart') spendChartRef?: ElementRef<HTMLCanvasElement>;

  userId = '';
  actId: string | null = null;

  tabs: string[] = ['Campaigns', 'Ad Sets', 'Ads'];
  activeTabIndex = 0;

  // Platform tabs
  platformTabs: { key: string; label: string; icon: string; connected: boolean }[] = [
    { key: 'META',      label: 'Meta',      icon: 'fa-brands fa-meta',    connected: false },
    { key: 'TIKTOK',    label: 'TikTok',    icon: 'fa-brands fa-tiktok',  connected: false },
    { key: 'GOOGLE',    label: 'Google Ads',icon: 'fa-brands fa-google',  connected: false },
    { key: 'LINKEDIN',  label: 'LinkedIn',  icon: 'fa-brands fa-linkedin',connected: false },
    { key: 'PINTEREST', label: 'Pinterest', icon: 'fa-brands fa-pinterest',connected: false },
    { key: 'REDDIT',    label: 'Reddit',    icon: 'fa-brands fa-reddit',  connected: false },
  ];
  activePlatform = 'META';
  isLoadingPlatforms = false;

  isLoading = false;
  isLoadingObjects = false;
  activePreset: number | null = null;
  private loadGeneration = 0;

  chartType: 'line' | 'bar' = 'line';
  private chartInstance: Chart | null = null;
  private spendChartInstance: Chart | null = null;

  startFocused = false;
  stopFocused = false;
  dateRangeSelected = false;

  // Date range
  dateStart = '';
  dateStop = '';

  // Available objects loaded from Meta services
  availableCampaigns: Campaign[] = [];
  availableAdSets: AdSetResponse[] = [];
  availableAds: AdResponse[] = [];

  // User-selected object IDs for targeted sync
  selectedCampaignIds: Set<string> = new Set();
  selectedAdSetIds: Set<string> = new Set();
  selectedAdIds: Set<string> = new Set();

  // Fields to request per object type
  selectedCampaignFields: Set<string> = new Set(CAMPAIGN_FIELDS);
  selectedAdSetFields: Set<string> = new Set(ADSET_FIELDS);
  selectedAdFields: Set<string> = new Set(AD_FIELDS);

  readonly campaignFieldOptions = CAMPAIGN_FIELDS;
  readonly adSetFieldOptions = ADSET_FIELDS;
  readonly adFieldOptions = AD_FIELDS;

  // Insight snapshots per type
  campaignInsights: InsightSnapshot[] = [];
  adSetInsights: InsightSnapshot[] = [];
  adInsights: InsightSnapshot[] = [];

  // 6 Metric Blocks
  metricBlocks: MetricBlock[] = [];

  // Metric modal
  isMetricModalOpen = false;
  activeBlockIndex = -1;
  tempMetricSelection = '';

  // Fields modal
  isFieldsModalOpen = false;
  fieldsModalTab: 'campaign' | 'adset' | 'ad' = 'campaign';

  allMetrics = ALL_INSIGHT_METRICS;

  // Tracks which tabs have had their data loaded at least once
  private tabDataLoaded = [false, false, false];

  // Static cache — persists across navigation until date range change or explicit sync
  private static insightsCache: Map<string, InsightSnapshot[]> = new Map();
  private static cachedCampaigns: Campaign[] | null = null;
  private static cachedAdSets: AdSetResponse[] | null = null;
  private static cachedAds: AdResponse[] | null = null;

  // Side panels
  showTopPerformers = false;
  showSpendDistribution = false;
  private navSubscription: Subscription = new Subscription();

  // Object selection panel expand/collapse
  selectionExpanded = true;

  // Per-column search strings
  campaignSearch = '';
  adSetSearch = '';
  adSearch = '';

  // Expand/collapse state (independent of selection)
  expandedCampaignIds: Set<string> = new Set();
  expandedAdSetIds: Set<string> = new Set();

  colsExpanded = true;
  // Tracks the date range that was last synced (POST) — persists across navigation
  private static syncedRange: { start: string; stop: string } | null = null;

  constructor(
    private readonly toastr: AppToastrService,
    private readonly authStore: AuthStoreService,
    private readonly insightsService: InsightsService,
    private readonly campaignService: CampaignService,
    private readonly adSetService: AdSetService,
    private readonly adService: AdService,
    private readonly oauthService: OAuthService,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.userId = this.authStore.getUserId();
    this.actId = this.authStore.getActId();
    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam !== null) {
      const idx = parseInt(tabParam, 10);
      if (!isNaN(idx) && idx >= 0 && idx < this.tabs.length) {
        this.activeTabIndex = idx;
      }
    }
    this.initDateRange();
    this.initMetricBlocks();
    this.loadAvailableObjects();
    this.loadConnectionStatus();
    this.navSubscription = this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        this.showTopPerformers = false;
        this.showSpendDistribution = false;
      });
  }

  ngOnDestroy(): void {
    this.navSubscription.unsubscribe();
  }

  // ---------- Init ----------

  private loadConnectionStatus(): void {
    this.isLoadingPlatforms = true;
    this.oauthService.getConnections().pipe(
      finalize(() => { this.isLoadingPlatforms = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (connections: AdAccountConnectionSummary[]) => {
        this.platformTabs = this.platformTabs.map(tab => ({
          ...tab,
          connected: connections.some(c => c.provider === tab.key && c.connected),
        }));
      },
      error: () => {
        // Fallback: mark META connected if actId present
        this.platformTabs = this.platformTabs.map(tab => ({
          ...tab,
          connected: tab.key === 'META' && !!this.actId,
        }));
      },
    });
  }

  setPlatformTab(key: string): void {
    const tab = this.platformTabs.find(t => t.key === key);
    if (tab && tab.connected) {
      this.activePlatform = key;
    }
  }

  private initDateRange(): void {
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);
    this.dateStop = this.formatDate(today);
    this.dateStart = this.formatDate(thirtyDaysAgo);
  }

  private initMetricBlocks(): void {
    this.metricBlocks = DEFAULT_METRICS.map((key, i) => ({
      index: i,
      metricKey: key,
      label: this.formatMetricLabel(key),
      value: '—',
      icon: METRIC_ICONS[key] ?? 'analytics',
    }));
  }

  // ---------- Cache Helpers ----------

  private cacheKey(type: 'campaign' | 'adset' | 'ad'): string {
    const ids =
      type === 'campaign'
        ? Array.from(this.selectedCampaignIds).sort().join(',')
        : type === 'adset'
          ? Array.from(this.selectedAdSetIds).sort().join(',')
          : Array.from(this.selectedAdIds).sort().join(',');
    return `${type}__${this.dateStart}__${this.dateStop}__${ids}`;
  }

  private getCached(
    type: 'campaign' | 'adset' | 'ad',
  ): InsightSnapshot[] | null {
    return InsightsComponent.insightsCache.get(this.cacheKey(type)) ?? null;
  }

  private setCache(
    type: 'campaign' | 'adset' | 'ad',
    data: InsightSnapshot[],
  ): void {
    InsightsComponent.insightsCache.set(this.cacheKey(type), data);
  }

  private clearCache(): void {
    InsightsComponent.insightsCache.clear();
    this.tabDataLoaded = [false, false, false];
  }

  // ---------- Load Available Objects ----------

  private loadAvailableObjects(): void {
    // Use cached objects to skip API calls on re-navigation
    if (
      InsightsComponent.cachedCampaigns &&
      InsightsComponent.cachedAdSets &&
      InsightsComponent.cachedAds
    ) {
      this.availableCampaigns = InsightsComponent.cachedCampaigns;
      this.availableAdSets = InsightsComponent.cachedAdSets;
      this.availableAds = InsightsComponent.cachedAds;
      if (!this.isLoading) {
        this.loadAllData();
      }
      this.cdr.detectChanges();
      return;
    }

    this.isLoadingObjects = true;
    let done = 0;
    const finish = () => {
      done++;
      if (done === 3) {
        InsightsComponent.cachedCampaigns = this.availableCampaigns;
        InsightsComponent.cachedAdSets = this.availableAdSets;
        InsightsComponent.cachedAds = this.availableAds;
        this.isLoadingObjects = false;
        if (!this.isLoading) {
          this.loadAllData();
        }
        this.cdr.detectChanges();
      }
    };

    this.campaignService.getAllByPlatform(Provider.META).subscribe({
      next: (res: any) => {
        this.availableCampaigns = res?.data ?? [];
        finish();
      },
      error: () => {
        finish();
      },
    });

    this.adSetService.getAllByPlatform(Provider.META).subscribe({
      next: (res: any) => {
        this.availableAdSets = res?.data ?? [];
        finish();
      },
      error: () => {
        finish();
      },
    });

    this.adService.getAllByPlatform(Provider.META).subscribe({
      next: (res: any) => {
        this.availableAds = res?.data ?? [];
        finish();
      },
      error: () => {
        finish();
      },
    });
  }

  loadAllData(): void {
    if (!this.actId) {
      this.toastr.warning('No Meta ad account linked to this user');
      return;
    }
    // Check insights cache first — avoids API calls on re-navigation
    const cachedC = this.getCached('campaign');
    const cachedA = this.getCached('adset');
    const cachedD = this.getCached('ad');
    if (cachedC && cachedA && cachedD) {
      this.campaignInsights = cachedC;
      this.adSetInsights = cachedA;
      this.adInsights = cachedD;
      this.tabDataLoaded = [true, true, true];
      this.refreshCurrentView();
      this.cdr.detectChanges();
      return;
    }
    // No cache — leave insights empty until the user explicitly syncs
  }

  /** POST — sync from Meta API; only called via the Sync button */
  syncAllData(): void {
    if (!this.actId) return;
    this.isLoading = true;
    const actId = this.actId;
    let remaining = 3;
    const tempCampaign: InsightSnapshot[] = [];
    const tempAdSet: InsightSnapshot[] = [];
    const tempAd: InsightSnapshot[] = [];

    const onAllDone = () => {
      remaining--;
      if (remaining === 0) {
        // All 3 syncs complete — populate insights atomically from sync responses
        this.campaignInsights = tempCampaign;
        this.adSetInsights = tempAdSet;
        this.adInsights = tempAd;
        this.setCache('campaign', this.campaignInsights);
        this.setCache('adset', this.adSetInsights);
        this.setCache('ad', this.adInsights);
        this.isLoading = false;
        this.cdr.detectChanges();
        this.tabDataLoaded = [true, true, true];
        this.refreshCurrentView();
      }
    };

    // --- Campaigns ---
    const campaignIds =
      this.selectedCampaignIds.size > 0
        ? Array.from(this.selectedCampaignIds)
        : this.availableCampaigns
            .filter((c) => c.externalId)
            .map((c) => c.externalId as string);

    const campaignBody: any = {
      provider: Provider.META,
      adAccountId: `act_${actId}`,
      objectType: 'CAMPAIGN',
      fetchMode: campaignIds.length === 1 ? 'PER_OBJECT' : 'BATCH_IDS',
      objectExternalIds: campaignIds,
      dateStart: this.dateStart,
      dateStop: this.dateStop,
      timeIncrement: 1,
      timeIncrementAllDays: true,
      fields: Array.from(this.selectedCampaignFields),
      actionBreakdowns: 'action_type',
      actionReportTime: 'impression',
    };

    this.insightsService
      .syncCampaignsBatch(campaignBody)
      .pipe(finalize(onAllDone))
      .subscribe({
        next: (res) => {
          tempCampaign.push(...(res?.data ?? []));
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync campaign insights'));
          }
        },
      });

    // --- Ad Sets ---
    const adSetIds =
      this.selectedAdSetIds.size > 0
        ? Array.from(this.selectedAdSetIds)
        : this.availableAdSets
            .filter((a) => a.externalId)
            .map((a) => a.externalId);

    const adSetBody: any = {
      provider: Provider.META,
      adAccountId: `act_${actId}`,
      objectType: 'ADSET',
      fetchMode: adSetIds.length === 1 ? 'PER_OBJECT' : 'BATCH_IDS',
      objectExternalIds: adSetIds,
      dateStart: this.dateStart,
      dateStop: this.dateStop,
      timeIncrement: 1,
      timeIncrementAllDays: true,
      fields: Array.from(this.selectedAdSetFields),
      actionBreakdowns: 'action_type',
      actionReportTime: 'impression',
    };

    this.insightsService
      .syncAdSetsBatch(adSetBody)
      .pipe(finalize(onAllDone))
      .subscribe({
        next: (res) => {
          tempAdSet.push(...(res?.data ?? []));
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync ad set insights'));
          }
        },
      });

    // --- Ads ---
    const adIds =
      this.selectedAdIds.size > 0
        ? Array.from(this.selectedAdIds)
        : this.availableAds
            .filter((a) => a.externalId)
            .map((a) => a.externalId);

    const adBody: any = {
      provider: Provider.META,
      adAccountId: `act_${actId}`,
      objectType: 'AD',
      fetchMode: adIds.length === 1 ? 'PER_OBJECT' : 'BATCH_IDS',
      objectExternalIds: adIds,
      dateStart: this.dateStart,
      dateStop: this.dateStop,
      timeIncrement: 1,
      timeIncrementAllDays: true,
      fields: Array.from(this.selectedAdFields),
      actionBreakdowns: 'action_type',
      actionReportTime: 'impression',
    };

    this.insightsService
      .syncAdsBatch(adBody)
      .pipe(finalize(onAllDone))
      .subscribe({
        next: (res) => {
          tempAd.push(...(res?.data ?? []));
        },
        error: (err: any) => {
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to sync ad insights'));
          }
        },
      });
  }

  private makeBatchDone(size: number): () => void {
    const gen = ++this.loadGeneration;
    let remaining = size;
    return () => {
      if (this.loadGeneration !== gen) return;
      remaining--;
      if (remaining === 0) {
        // Dismiss spinner immediately before any rendering work
        this.isLoading = false;
        this.cdr.detectChanges();
        this.tabDataLoaded = [true, true, true];
        this.refreshCurrentView();
      }
    };
  }

  fetchCampaignInsights(): void {
    if (!this.actId) return;
    this.isLoading = true;
    const batchDone = this.makeBatchDone(1);
    const ids =
      this.selectedCampaignIds.size > 0
        ? Array.from(this.selectedCampaignIds)
        : undefined;
    this.insightsService
      .queryCampaigns(
        Provider.META,
        this.actId,
        this.dateStart,
        this.dateStop,
        ids,
      )
      .pipe(finalize(batchDone))
      .subscribe({
        next: (res) => {
          this.campaignInsights = res?.data ?? [];
        },
        error: () => {
          this.isLoading = false;
          this.cdr.detectChanges();
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error('Failed to fetch campaign insights');
          }
        },
      });
  }

  fetchAdSetInsights(): void {
    if (!this.actId) return;
    this.isLoading = true;
    const batchDone = this.makeBatchDone(1);
    const ids =
      this.selectedAdSetIds.size > 0
        ? Array.from(this.selectedAdSetIds)
        : undefined;
    this.insightsService
      .queryAdSets(
        Provider.META,
        this.actId,
        this.dateStart,
        this.dateStop,
        ids,
      )
      .pipe(finalize(batchDone))
      .subscribe({
        next: (res) => {
          this.adSetInsights = res?.data ?? [];
        },
        error: () => {
          this.isLoading = false;
          this.cdr.detectChanges();
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error('Failed to fetch ad set insights');
          }
        },
      });
  }

  fetchAdInsights(): void {
    if (!this.actId) return;
    this.isLoading = true;
    const batchDone = this.makeBatchDone(1);
    const ids =
      this.selectedAdIds.size > 0 ? Array.from(this.selectedAdIds) : undefined;
    this.insightsService
      .queryAds(Provider.META, this.actId, this.dateStart, this.dateStop, ids)
      .pipe(finalize(batchDone))
      .subscribe({
        next: (res) => {
          this.adInsights = res?.data ?? [];
        },
        error: () => {
          this.isLoading = false;
          this.cdr.detectChanges();
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error('Failed to fetch ad insights');
          }
        },
      });
  }

  // ---------- Tab GET Fetch ----------

  private fetchTabData(tabIndex: number): void {
    if (!this.actId) return;
    const type = tabIndex === 0 ? 'campaign' : tabIndex === 1 ? 'adset' : 'ad';
    const cached = this.getCached(type);
    if (cached) {
      if (tabIndex === 0) this.campaignInsights = cached;
      else if (tabIndex === 1) this.adSetInsights = cached;
      else this.adInsights = cached;
      this.tabDataLoaded[tabIndex] = true;
      this.refreshCurrentView();
      this.cdr.detectChanges();
      return;
    }
    this.isLoading = true;
    const actId = this.actId;
    const campIds =
      this.selectedCampaignIds.size > 0
        ? Array.from(this.selectedCampaignIds)
        : undefined;
    const adSetIds =
      this.selectedAdSetIds.size > 0
        ? Array.from(this.selectedAdSetIds)
        : undefined;
    const adIds =
      this.selectedAdIds.size > 0 ? Array.from(this.selectedAdIds) : undefined;
    const onDone = (snapshots: InsightSnapshot[]) => {
      if (tabIndex === 0) this.campaignInsights = snapshots;
      else if (tabIndex === 1) this.adSetInsights = snapshots;
      else this.adInsights = snapshots;
      this.setCache(type, snapshots);
      this.tabDataLoaded[tabIndex] = true;
    };
    const onFail = (msg: string) => {
      this.isLoading = false;
      this.cdr.detectChanges();
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error(msg);
      }
    };
    const onFinalize = () => {
      // Dismiss spinner immediately before any rendering work
      this.isLoading = false;
      this.cdr.detectChanges();
      this.refreshCurrentView();
    };
    if (tabIndex === 0) {
      this.insightsService
        .queryCampaigns(
          Provider.META,
          actId,
          this.dateStart,
          this.dateStop,
          campIds,
        )
        .pipe(finalize(onFinalize))
        .subscribe({
          next: (r) => onDone(r?.data ?? []),
          error: () => onFail('Failed to fetch campaign insights'),
        });
    } else if (tabIndex === 1) {
      this.insightsService
        .queryAdSets(
          Provider.META,
          actId,
          this.dateStart,
          this.dateStop,
          adSetIds,
        )
        .pipe(finalize(onFinalize))
        .subscribe({
          next: (r) => onDone(r?.data ?? []),
          error: () => onFail('Failed to fetch ad set insights'),
        });
    } else {
      this.insightsService
        .queryAds(Provider.META, actId, this.dateStart, this.dateStop, adIds)
        .pipe(finalize(onFinalize))
        .subscribe({
          next: (r) => onDone(r?.data ?? []),
          error: () => onFail('Failed to fetch ad insights'),
        });
    }
  }

  private refreshCurrentView(): void {
    this.updateBlockValues();
    this.cdr.detectChanges();
    setTimeout(() => {
      this.drawGraph();
      this.drawSpendChart();
    }, 100);
  }

  // ---------- Tab ----------

  getCurrentSnapshots(): InsightSnapshot[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetInsights;
      case 2:
        return this.adInsights;
      default:
        return this.campaignInsights;
    }
  }

  onTabChange(tabIndex: number): void {
    this.activeTabIndex = tabIndex;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tabIndex },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    if (this.tabDataLoaded[tabIndex]) {
      // Data already loaded — just re-render, no spinner
      this.refreshCurrentView();
    } else {
      this.fetchTabData(tabIndex);
    }
  }

  // ---------- Metric Blocks ----------

  updateBlockValues(): void {
    const snapshots = this.getCurrentSnapshots();
    const tabType =
      this.activeTabIndex === 0
        ? 'campaign'
        : this.activeTabIndex === 1
          ? 'adset'
          : 'ad';
    const durMs =
      new Date(this.dateStop).getTime() - new Date(this.dateStart).getTime();
    const prevStop = new Date(new Date(this.dateStart).getTime() - 86400000);
    const prevStart = new Date(prevStop.getTime() - durMs);
    const prevKey = `${tabType}__${this.formatDate(prevStart)}__${this.formatDate(prevStop)}`;
    const prevSnaps = InsightsComponent['insightsCache'].get(prevKey) ?? null;

    this.metricBlocks = this.metricBlocks.map((block) => {
      const value = this.aggregateMetric(block.metricKey, snapshots);
      let trend: number | undefined;
      let trendDirection: 'up' | 'down' | 'neutral' | undefined;
      if (prevSnaps && block.metricKey) {
        const prevVal = this.aggregateMetricRaw(block.metricKey, prevSnaps);
        const curVal = this.aggregateMetricRaw(block.metricKey, snapshots);
        if (prevVal > 0) {
          trend = ((curVal - prevVal) / prevVal) * 100;
          trendDirection = trend > 1 ? 'up' : trend < -1 ? 'down' : 'neutral';
        }
      }
      return {
        ...block,
        label: block.metricKey
          ? this.formatMetricLabel(block.metricKey)
          : 'Click to select',
        value,
        icon: block.metricKey
          ? (METRIC_ICONS[block.metricKey] ?? 'analytics')
          : 'add_circle',
        trend,
        trendDirection,
      };
    });
  }

  /**
   * Extracts a numeric value from a rawData entry for a given metric key.
   * Handles both flat fields (e.g. "spend") and dot-notation array fields
   * (e.g. "video_play_actions.video_view", "actions.offsite_conversion.fb_pixel_purchase").
   */
  private extractMetricValue(entry: any, metricKey: string): { value: number; found: boolean } {
    const dotIdx = metricKey.indexOf('.');
    if (dotIdx === -1) {
      const val = entry[metricKey];
      if (val === undefined || val === null || val === '') return { value: 0, found: false };
      return { value: parseFloat(String(val)) || 0, found: true };
    }
    // Dot-notation: fieldName.action_type (action_type may itself contain dots)
    const fieldName = metricKey.substring(0, dotIdx);
    const actionType = metricKey.substring(dotIdx + 1);
    const arr = entry[fieldName];
    if (!Array.isArray(arr)) return { value: 0, found: false };
    const item = arr.find((a: any) => a.action_type === actionType);
    if (!item) return { value: 0, found: false };
    return { value: parseFloat(String(item.value)) || 0, found: true };
  }

  private aggregateMetricRaw(
    metricKey: string,
    snapshots: InsightSnapshot[],
  ): number {
    let total = 0;
    for (const snap of snapshots) {
      for (const entry of snap.rawData?.data ?? []) {
        const { value } = this.extractMetricValue(entry, metricKey);
        total += value;
      }
    }
    return total;
  }

  aggregateMetric(
    metricKey: string | null,
    snapshots: InsightSnapshot[],
  ): string {
    if (!metricKey) return '—';
    let total = 0;
    let hasData = false;
    for (const snap of snapshots) {
      const entries: any[] = snap.rawData?.data ?? [];
      for (const entry of entries) {
        const { value, found } = this.extractMetricValue(entry, metricKey);
        if (found) {
          hasData = true;
          total += value;
        }
      }
    }
    if (!hasData) return '—';
    return this.formatValue(total, this.getMetricFormat(metricKey));
  }

  formatValue(value: number, format: 'number' | 'currency' | 'percent' | 'decimal2'): string {
    switch (format) {
      case 'currency': return '$' + this.formatNumber(value);
      case 'percent': return value.toFixed(2) + '%';
      case 'decimal2': return value.toFixed(2);
      default: return this.formatNumber(value);
    }
  }

  getMetricFormat(key: string): 'number' | 'currency' | 'percent' | 'decimal2' {
    return METRIC_CONFIG.find(m => m.key === key)?.format ?? 'number';
  }

  getMetricCategories(): string[] {
    return [...new Set(METRIC_CONFIG.map(m => m.category))];
  }

  getMetricsByCategory(category: string): MetricConfig[] {
    return METRIC_CONFIG.filter(m => m.category === category);
  }

  // ---------- Metric Modal ----------

  openMetricModal(blockIndex: number): void {
    this.activeBlockIndex = blockIndex;
    this.tempMetricSelection = this.metricBlocks[blockIndex].metricKey ?? '';
    this.isMetricModalOpen = true;
  }

  closeMetricModal(): void {
    this.isMetricModalOpen = false;
    this.activeBlockIndex = -1;
    this.tempMetricSelection = '';
  }

  applyMetricSelection(): void {
    if (this.activeBlockIndex < 0) return;
    const block = this.metricBlocks[this.activeBlockIndex];
    block.metricKey = this.tempMetricSelection || null;
    block.label = block.metricKey
      ? this.formatMetricLabel(block.metricKey)
      : 'Click to select';
    block.icon = block.metricKey
      ? (METRIC_ICONS[block.metricKey] ?? 'analytics')
      : 'add_circle';
    block.value = this.aggregateMetric(
      block.metricKey,
      this.getCurrentSnapshots(),
    );
    this.closeMetricModal();
    setTimeout(() => this.drawGraph(), 100);
  }

  getAvailableMetrics(): string[] {
    const usedKeys = new Set(
      this.metricBlocks
        .filter((b, i) => i !== this.activeBlockIndex && b.metricKey)
        .map((b) => b.metricKey as string),
    );
    return this.allMetrics.filter((m) => !usedKeys.has(m));
  }

  // ---------- Filtered Object Lists (search) ----------

  get filteredCampaigns(): Campaign[] {
    const q = this.campaignSearch.toLowerCase().trim();
    if (!q) return this.availableCampaigns;
    return this.availableCampaigns.filter(
      (c) =>
        c.name?.toLowerCase().includes(q) ||
        c.externalId?.toLowerCase().includes(q),
    );
  }

  get filteredAdSets(): AdSetResponse[] {
    let source = this.availableAdSets;
    if (this.selectedCampaignIds.size > 0) {
      const selectedCampaignDbIds = new Set(
        this.availableCampaigns
          .filter(c => c.id != null && c.externalId != null && this.selectedCampaignIds.has(c.externalId))
          .map(c => c.id as number),
      );
      source = source.filter(a => selectedCampaignDbIds.has(a.campaignId));
    }
    const q = this.adSetSearch.toLowerCase().trim();
    if (!q) return source;
    return source.filter(
      (a) =>
        a.name?.toLowerCase().includes(q) ||
        a.externalId?.toLowerCase().includes(q),
    );
  }

  get filteredAds(): AdResponse[] {
    let source = this.availableAds;
    if (this.selectedAdSetIds.size > 0) {
      const selectedAdSetDbIds = new Set(
        this.availableAdSets
          .filter(a => a.externalId != null && this.selectedAdSetIds.has(a.externalId))
          .map(a => a.id),
      );
      source = source.filter(a => selectedAdSetDbIds.has(a.adSetId));
    }
    const q = this.adSearch.toLowerCase().trim();
    if (!q) return source;
    return source.filter((a) => a.name?.toLowerCase().includes(q));
  }

  get adSetsCascadeActive(): boolean {
    return this.selectedCampaignIds.size > 0;
  }

  get adsCascadeActive(): boolean {
    return this.selectedAdSetIds.size > 0;
  }

  getAdSetsForCampaign(c: Campaign): AdSetResponse[] {
    return this.availableAdSets.filter(a => a.campaignId === c.id);
  }

  getAdsForAdSet(a: AdSetResponse): AdResponse[] {
    return this.availableAds.filter(ad => ad.adSetId === a.id);
  }

  clearAll(): void {
    this.clearCampaigns();
    this.clearAdSets();
    this.clearAds();
  }

  // ---------- Date Range ----------

  onCustomRangeChange(range: DateRange): void {
    if (range.from && range.to) {
      this.dateStart = this.formatDate(range.from);
      this.dateStop = this.formatDate(range.to);
      this.dateRangeSelected = true;
      this.onDateRangeChange();
    }
  }

  onQuickSelectChange(key: string): void {
    const days = key === '7d' ? 7 : key === '14d' ? 14 : key === '30d' ? 30 : 90;
    this.activePreset = days;
    this.dateRangeSelected = true;
  }

  onDateRangeChange(): void {
    if (this.dateStart && this.dateStop) {
      this.clearCache();
      this.loadAllData();
    }
  }

  // ---------- Graph ----------

  setChartType(type: 'line' | 'bar'): void {
    this.chartType = type;
    this.drawGraph();
  }

  setPreset(days: number): void {
    this.activePreset = days;
    const today = new Date();
    const from = new Date();
    from.setDate(today.getDate() - days);
    this.dateStop = this.formatDate(today);
    this.dateStart = this.formatDate(from);
    this.onDateRangeChange();
  }

  drawGraph(): void {
    const canvas = this.graphCanvasRef?.nativeElement;
    if (!canvas) return;
    if (this.chartInstance) {
      this.chartInstance.destroy();
      this.chartInstance = null;
    }

    const snapshots = this.getCurrentSnapshots();
    if (!snapshots.length) return;

    const dateMap = new Map<string, any[]>();
    for (const snap of snapshots) {
      for (const entry of snap.rawData?.data ?? []) {
        const key: string = entry['date_start'] ?? snap.dateStart;
        if (!dateMap.has(key)) dateMap.set(key, []);
        dateMap.get(key)!.push(entry);
      }
    }
    const dates = Array.from(dateMap.keys()).sort();
    const activeBlocks = this.metricBlocks.filter((b) => b.metricKey);

    const datasets = activeBlocks.map((block, si) => ({
      label: this.formatMetricLabel(block.metricKey!),
      data: dates.map((d) => {
        const entries = dateMap.get(d) ?? [];
        return entries.reduce((sum: number, e: any) => {
          const { value } = this.extractMetricValue(e, block.metricKey!);
          return sum + value;
        }, 0);
      }),
      borderColor: CHART_COLORS[si % CHART_COLORS.length],
      backgroundColor: CHART_COLORS[si % CHART_COLORS.length] + '22',
      fill: true,
      tension: 0.4,
      pointRadius: 4,
      pointHoverRadius: 7,
      borderWidth: 2.5,
    }));

    this.chartInstance = new Chart(canvas, {
      type: this.chartType,
      data: { labels: dates.map((d) => d.slice(5)), datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: {
            position: 'top',
            labels: {
              font: { family: 'Inter, sans-serif', size: 12 },
              usePointStyle: true,
            },
          },
          tooltip: {
            callbacks: {
              label: (ctx: any) =>
                ` ${ctx.dataset.label}: ${this.formatNumber(ctx.parsed.y)}`,
            },
          },
        },
        scales: {
          x: {
            grid: { color: '#e8ebf2' },
            ticks: { font: { size: 11 }, maxRotation: 45, color: '#64748b' },
          },
          y: {
            grid: { color: '#e8ebf2' },
            ticks: {
              font: { size: 11 },
              color: '#64748b',
              callback: (value: any) => this.formatNumber(Number(value)),
            },
          },
        },
      },
    } as any);
  }

  drawSpendChart(): void {
    const canvas = this.spendChartRef?.nativeElement;
    if (!canvas) return;
    if (this.spendChartInstance) {
      this.spendChartInstance.destroy();
      this.spendChartInstance = null;
    }
    const performers = this.getTopPerformers(6);
    if (!performers.length) return;
    this.spendChartInstance = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: performers.map((p) => p.name),
        datasets: [
          {
            data: performers.map((p) => p.spend),
            backgroundColor: CHART_COLORS,
            borderWidth: 2,
            borderColor: '#fff',
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right' },
          tooltip: {
            callbacks: {
              label: (ctx: any) => ` $${(ctx.parsed as number).toFixed(2)}`,
            },
          },
        },
      },
    } as any);
  }

  getTopPerformers(n: number): any[] {
    const snapshots = this.getCurrentSnapshots();
    const sourceList =
      this.activeTabIndex === 0
        ? this.availableCampaigns
        : this.activeTabIndex === 1
          ? this.availableAdSets
          : this.availableAds;
    const nameMap = new Map(
      sourceList.map((o: any) => [o.externalId, o.name as string]),
    );
    return snapshots
      .map((snap) => {
        const entries: any[] = snap.rawData?.data ?? [];
        const agg = (key: string) =>
          entries.reduce((s, e) => s + (parseFloat(e[key]) || 0), 0);
        return {
          name:
            nameMap.get(snap.objectExternalId) ??
            snap.objectExternalId ??
            'Unknown',
          spend: agg('spend'),
          impressions: agg('impressions'),
          clicks: agg('clicks'),
          ctr: entries.length ? agg('ctr') / entries.length : 0,
          cpc: entries.length ? agg('cpc') / entries.length : 0,
          score: this.computePerformanceScore(snap),
        };
      })
      .sort((a, b) => b.spend - a.spend)
      .slice(0, n);
  }

  computePerformanceScore(snap: InsightSnapshot): number {
    const entries: any[] = snap.rawData?.data ?? [];
    if (!entries.length) return 0;
    const avg = (key: string) =>
      entries.reduce((s, e) => s + (parseFloat(e[key]) || 0), 0) /
      entries.length;
    const ctr = avg('ctr');
    const cpc = avg('cpc');
    const freq = avg('frequency');
    const ctrScore = Math.min(ctr * 10, 30);
    const cpcScore = cpc > 0 ? Math.min(30 / cpc, 30) : 0;
    const freqScore = freq <= 3 ? 20 : Math.max(20 - (freq - 3) * 4, 0);
    const spendScore = 20;
    return Math.round(
      Math.min(ctrScore + cpcScore + freqScore + spendScore, 100),
    );
  }

  scoreClass(score: number): string {
    return score >= 80
      ? 'score-green'
      : score >= 50
        ? 'score-yellow'
        : 'score-red';
  }

  // ---------- Object Selection ----------

  isCampaignSelected(c: Campaign): boolean {
    return this.selectedCampaignIds.has(c.externalId ?? '');
  }

  isAdSetSelected(a: AdSetResponse): boolean {
    return this.selectedAdSetIds.has(a.externalId ?? '');
  }

  isAdSelected(a: AdResponse): boolean {
    return this.selectedAdIds.has(a.externalId ?? '');
  }

  toggleCampaign(c: Campaign): void {
    const id = c.externalId ?? '';
    if (!id) return;
    if (this.selectedCampaignIds.has(id)) this.selectedCampaignIds.delete(id);
    else this.selectedCampaignIds.add(id);
  }

  toggleAdSet(a: AdSetResponse): void {
    const id = a.externalId ?? '';
    if (!id) return;
    if (this.selectedAdSetIds.has(id)) this.selectedAdSetIds.delete(id);
    else this.selectedAdSetIds.add(id);
  }

  toggleAd(a: AdResponse): void {
    const id = a.externalId ?? '';
    if (!id) return;
    if (this.selectedAdIds.has(id)) this.selectedAdIds.delete(id);
    else this.selectedAdIds.add(id);
  }

  isCampaignExpanded(c: Campaign): boolean {
    return this.expandedCampaignIds.has(c.externalId ?? '');
  }

  isAdSetExpanded(a: AdSetResponse): boolean {
    return this.expandedAdSetIds.has(a.externalId ?? '');
  }

  toggleCampaignExpand(c: Campaign, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const id = c.externalId ?? '';
    if (!id) return;
    if (this.expandedCampaignIds.has(id)) this.expandedCampaignIds.delete(id);
    else this.expandedCampaignIds.add(id);
  }

  toggleAdSetExpand(a: AdSetResponse, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const id = a.externalId ?? '';
    if (!id) return;
    if (this.expandedAdSetIds.has(id)) this.expandedAdSetIds.delete(id);
    else this.expandedAdSetIds.add(id);
  }

  selectAllCampaigns(): void {
    this.availableCampaigns.forEach((c) => {
      if (c.externalId) this.selectedCampaignIds.add(c.externalId);
    });
    this.availableAdSets.forEach((a) => {
      if (a.externalId) this.selectedAdSetIds.add(a.externalId);
    });
    this.availableAds.forEach((a) => {
      if (a.externalId) this.selectedAdIds.add(a.externalId);
    });
  }

  clearCampaigns(): void {
    const key = this.cacheKey('campaign');
    this.selectedCampaignIds.clear();
    this.campaignInsights = [];
    InsightsComponent.insightsCache.delete(key);
    this.tabDataLoaded[0] = false;
    this.refreshCurrentView();
  }

  selectAllAdSets(): void {
    this.availableAdSets.forEach((a) => {
      if (a.externalId) this.selectedAdSetIds.add(a.externalId);
    });
  }

  clearAdSets(): void {
    const key = this.cacheKey('adset');
    this.selectedAdSetIds.clear();
    this.adSetInsights = [];
    InsightsComponent.insightsCache.delete(key);
    this.tabDataLoaded[1] = false;
    this.refreshCurrentView();
  }

  selectAllAds(): void {
    this.availableAds.forEach((a) => {
      if (a.externalId) this.selectedAdIds.add(a.externalId);
    });
  }

  clearAds(): void {
    const key = this.cacheKey('ad');
    this.selectedAdIds.clear();
    this.adInsights = [];
    InsightsComponent.insightsCache.delete(key);
    this.tabDataLoaded[2] = false;
    this.refreshCurrentView();
  }

  selectionSummary(tab: 'campaign' | 'adset' | 'ad'): string {
    if (tab === 'campaign') {
      const n = this.selectedCampaignIds.size;
      return n === 0
        ? `All (${this.availableCampaigns.length})`
        : `${n} selected`;
    }
    if (tab === 'adset') {
      const n = this.selectedAdSetIds.size;
      return n === 0 ? `All (${this.availableAdSets.length})` : `${n} selected`;
    }
    const n = this.selectedAdIds.size;
    return n === 0 ? `All (${this.availableAds.length})` : `${n} selected`;
  }

  syncSelected(): void {
    this.clearCache();
    // Object lists (campaigns/adsets/ads) are DB-only and don't change during an insights sync;
    // keep them cached so the panel stays responsive.
    this.syncAllData();
  }

  hasSelectedObjects(): boolean {
    return this.selectedCampaignIds.size > 0 ||
           this.selectedAdSetIds.size > 0 ||
           this.selectedAdIds.size > 0;
  }

  // ---------- Fields Selection Modal ----------

  openFieldsModal(tab: 'campaign' | 'adset' | 'ad'): void {
    this.fieldsModalTab = tab;
    this.isFieldsModalOpen = true;
  }

  closeFieldsModal(): void {
    this.isFieldsModalOpen = false;
  }

  getActiveFieldSet(): Set<string> {
    if (this.fieldsModalTab === 'campaign') return this.selectedCampaignFields;
    if (this.fieldsModalTab === 'adset') return this.selectedAdSetFields;
    return this.selectedAdFields;
  }

  getActiveFieldOptions(): string[] {
    if (this.fieldsModalTab === 'campaign') return this.campaignFieldOptions;
    if (this.fieldsModalTab === 'adset') return this.adSetFieldOptions;
    return this.adFieldOptions;
  }

  isFieldSelected(field: string): boolean {
    return this.getActiveFieldSet().has(field);
  }

  toggleField(field: string): void {
    const set = this.getActiveFieldSet();
    if (set.has(field)) set.delete(field);
    else set.add(field);
  }

  selectAllFields(): void {
    this.getActiveFieldOptions().forEach((f) =>
      this.getActiveFieldSet().add(f),
    );
  }

  clearAllFields(): void {
    this.getActiveFieldSet().clear();
  }

  fieldsSummary(tab: 'campaign' | 'adset' | 'ad'): string {
    if (tab === 'campaign')
      return `${this.selectedCampaignFields.size} / ${this.campaignFieldOptions.length} fields`;
    if (tab === 'adset')
      return `${this.selectedAdSetFields.size} / ${this.adSetFieldOptions.length} fields`;
    return `${this.selectedAdFields.size} / ${this.adFieldOptions.length} fields`;
  }

  // ---------- Helpers ----------

  private formatDate(d: Date): string {
    return d.toISOString().split('T')[0];
  }

  formatMetricLabel(key: string): string {
    const config = METRIC_CONFIG.find(m => m.key === key);
    if (config) return config.label;
    return key
      .replaceAll('.', ' › ')
      .replaceAll('_', ' ')
      .replace(/\b\w/g, (c) => c.toUpperCase());
  }

  private formatNumber(val: number): string {
    if (val >= 1_000_000) return (val / 1_000_000).toFixed(1) + 'M';
    if (val >= 1_000) return (val / 1_000).toFixed(1) + 'K';
    return Number.isInteger(val) ? String(val) : val.toFixed(2);
  }
}
