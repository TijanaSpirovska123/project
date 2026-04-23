import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectorRef,
  ViewChild,
  ElementRef,
  HostListener,
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
import { DatePresetId, DateRangeSelection } from './adflow-date-range-picker.component';
import { InsightsSavedViewService } from '../../services/insights/insights-saved-view.service';
import { InsightsSavedView, InsightsViewConfig } from '../../models/insights/insights-saved-view.model';
import {
  CHART_COLORS,
  DEFAULT_DATE_SELECTION,
  ROLLING_PRESET_DAYS,
  CAMPAIGN_FIELDS,
  ADSET_FIELDS,
  AD_FIELDS,
  DEFAULT_VISIBLE_METRICS,
  METRIC_GROUPS_DEF,
  PLATFORM_META,
} from '../../data/insights/insights-fields';

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
  activePreset: number | null = 30;
  activePresetKey: DatePresetId | null = 'last_30d';
  dateSelection: DateRangeSelection = { ...DEFAULT_DATE_SELECTION };
  private loadGeneration = 0;

  private static readonly LAST_VIEW_KEY = 'adflow.insights.lastView.v1';

  chartType: 'line' | 'bar' = 'line';
  private chartInstance: Chart | null = null;
  private spendChartInstance: Chart | null = null;

  startFocused = false;
  stopFocused = false;
  dateRangeSelected = true;

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

  // ---------- Change 1 — Unified tree + level selector ----------
  treeLevel: 'all' | 'campaigns' | 'adSets' | 'ads' = 'all';
  selectedTreeRow: { objectType: 'CAMPAIGN' | 'ADSET' | 'AD'; objectExternalId: string } | null = null;
  treeSearch = '';

  // ---------- Change 2 — Visible metric chips ----------
  visibleMetrics: { campaigns: string[]; adSets: string[]; ads: string[] } = {
    campaigns: [...DEFAULT_VISIBLE_METRICS],
    adSets: [...DEFAULT_VISIBLE_METRICS],
    ads: [...DEFAULT_VISIBLE_METRICS],
  };
  metricDropdownOpen = false;
  metricDropdownSearch = '';
  private metricsIntersectionToastShown = false;
  readonly metricGroupsDef = METRIC_GROUPS_DEF;

  // ---------- Change 4 — Multi-platform ----------
  platformColumnVisible = false;
  sideBySideMode = false;
  groupBy: 'none' | 'platform' = 'none';
  selectedPlatforms: string[] = ['META'];
  readonly platformMeta = PLATFORM_META;

  // ---------- Change 5 — Sort + bulk ----------
  sortBy: { column: string; direction: 'asc' | 'desc' } | null = null;

  // Viewport width for side-by-side availability check
  private viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1440;

  // ---------- Saved Views state ----------
  savedViews: InsightsSavedView[] = [];
  activeSavedView: InsightsSavedView | null = null;
  savedViewsModified = false;
  savedViewsDropdownOpen = false;
  savedViewsLoading = false;
  private initialSavedViewAutoApplied = false;

  // Save dialog
  saveDialogOpen = false;
  saveDialogName = '';
  saveDialogDescription = '';
  saveDialogPin = false;
  saveDialogSaving = false;
  saveDialogError = '';

  // Manage drawer
  manageDrawerOpen = false;
  manageDrawerViews: InsightsSavedView[] = [];
  manageDrawerLoading = false;

  // Per-row three-dot menu in dropdown
  svMenuViewId: number | null = null;

  // Delete confirmation dialog
  deleteConfirmOpen = false;
  deleteConfirmView: InsightsSavedView | null = null;

  // Rename dialog
  renameDialogOpen = false;
  renameDialogView: InsightsSavedView | null = null;
  renameDialogName = '';
  renameDialogError = '';
  renameDialogSaving = false;

  constructor(
    private readonly toastr: AppToastrService,
    private readonly authStore: AuthStoreService,
    private readonly insightsService: InsightsService,
    private readonly savedViewService: InsightsSavedViewService,
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
    if (!this.restoreLastView()) {
      this.initDateRange();
    }
    this.initMetricBlocks();
    this.loadAvailableObjects();
    this.loadConnectionStatus();
    this.autoApplyMostRecentSavedView();
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

  autoApplyMostRecentSavedView(): void {
    if (this.initialSavedViewAutoApplied) return;

    this.initialSavedViewAutoApplied = true;
    this.savedViewsLoading = true;
    this.savedViewService.list(this.activePlatform).pipe(
      finalize(() => { this.savedViewsLoading = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (views) => {
        this.savedViews = views ?? [];
        const mostRecentView = this.savedViews[0];
        if (mostRecentView) {
          this.applySavedView(mostRecentView);
        }
      },
      error: () => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error('Failed to load saved views');
        }
      },
    });
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    if (typeof window !== 'undefined') {
      this.viewportWidth = window.innerWidth;
      if (this.viewportWidth < 1024 && this.sideBySideMode) {
        this.sideBySideMode = false;
        this.cdr.detectChanges();
      }
    }
  }

  // ---------- Change 1 — Tree level computed properties ----------

  get isSideBySideAvailable(): boolean {
    return this.viewportWidth >= 1024;
  }

  get connectedPlatformCount(): number {
    return this.platformTabs.filter(t => t.connected).length;
  }

  // ---------- Change 2 — Visible metrics computed properties ----------

  get currentVisibleMetrics(): string[] {
    switch (this.treeLevel) {
      case 'campaigns': return this.visibleMetrics.campaigns;
      case 'adSets': return this.visibleMetrics.adSets;
      case 'ads': return this.visibleMetrics.ads;
      default: {
        // Intersection across all three level sets
        const adSetSet = new Set(this.visibleMetrics.adSets);
        const adSet = new Set(this.visibleMetrics.ads);
        return this.visibleMetrics.campaigns.filter(m => adSetSet.has(m) && adSet.has(m));
      }
    }
  }

  get filteredMetricGroups(): { label: string; metrics: string[] }[] {
    const q = this.metricDropdownSearch.toLowerCase().trim();
    return METRIC_GROUPS_DEF.map(group => ({
      label: group.label,
      metrics: group.metrics.filter(m => !q || this.formatMetricLabel(m).toLowerCase().includes(q) || m.includes(q)),
    })).filter(g => g.metrics.length > 0);
  }

  // ---------- Change 3 — Tree-to-graph linking ----------

  get selectedTreeRowName(): string {
    if (!this.selectedTreeRow) return '';
    const { objectType, objectExternalId } = this.selectedTreeRow;
    if (objectType === 'CAMPAIGN') {
      return this.availableCampaigns.find(c => c.externalId === objectExternalId)?.name ?? objectExternalId;
    }
    if (objectType === 'ADSET') {
      return this.availableAdSets.find(a => a.externalId === objectExternalId)?.name ?? objectExternalId;
    }
    return this.availableAds.find(a => a.externalId === objectExternalId)?.name ?? objectExternalId;
  }

  get aggregateCountLabel(): string {
    const snapshots = this.getCurrentSnapshots();
    const n = new Set(snapshots.map(s => s.objectExternalId)).size;
    const type = this.activeTabIndex === 0 ? 'campaigns' : this.activeTabIndex === 1 ? 'ad sets' : 'ads';
    const range = this.activePreset ? `Last ${this.activePreset} days` : 'custom range';
    return `${n} ${type}, ${range}`;
  }

  getGraphSnapshots(): InsightSnapshot[] {
    if (!this.selectedTreeRow) return this.getCurrentSnapshots();
    const { objectType, objectExternalId } = this.selectedTreeRow;
    const pool = objectType === 'CAMPAIGN' ? this.campaignInsights
               : objectType === 'ADSET' ? this.adSetInsights
               : this.adInsights;
    return pool.filter(s => s.objectExternalId === objectExternalId);
  }

  // ---------- Change 5 — Sorted tree rows ----------

  get sortedCampaignRows(): Campaign[] {
    const q = this.treeSearch.toLowerCase().trim();
    let rows = q
      ? this.availableCampaigns.filter(c =>
          c.name?.toLowerCase().includes(q) || c.externalId?.toLowerCase().includes(q))
      : this.availableCampaigns;
    if (!this.sortBy) return rows;
    const { column, direction } = this.sortBy;
    const dir = direction === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = this.aggregateMetricRaw(column,
        this.campaignInsights.filter(s => s.objectExternalId === a.externalId));
      const bv = this.aggregateMetricRaw(column,
        this.campaignInsights.filter(s => s.objectExternalId === b.externalId));
      return (av - bv) * dir;
    });
  }

  get sortedAdSetRows(): AdSetResponse[] {
    const q = this.treeSearch.toLowerCase().trim();
    let rows = q
      ? this.availableAdSets.filter(a =>
          a.name?.toLowerCase().includes(q) || a.externalId?.toLowerCase().includes(q))
      : this.availableAdSets;
    if (!this.sortBy) return rows;
    const { column, direction } = this.sortBy;
    const dir = direction === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = this.aggregateMetricRaw(column,
        this.adSetInsights.filter(s => s.objectExternalId === a.externalId));
      const bv = this.aggregateMetricRaw(column,
        this.adSetInsights.filter(s => s.objectExternalId === b.externalId));
      return (av - bv) * dir;
    });
  }

  get sortedAdRows(): AdResponse[] {
    const q = this.treeSearch.toLowerCase().trim();
    let rows = q
      ? this.availableAds.filter(a => a.name?.toLowerCase().includes(q))
      : this.availableAds;
    if (!this.sortBy) return rows;
    const { column, direction } = this.sortBy;
    const dir = direction === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = this.aggregateMetricRaw(column,
        this.adInsights.filter(s => s.objectExternalId === a.externalId));
      const bv = this.aggregateMetricRaw(column,
        this.adInsights.filter(s => s.objectExternalId === b.externalId));
      return (av - bv) * dir;
    });
  }

  get checkedRowCount(): number {
    return this.selectedCampaignIds.size + this.selectedAdSetIds.size + this.selectedAdIds.size;
  }

  getPlatformInfo(key: string): { icon: string; label: string } {
    return PLATFORM_META[key] ?? { icon: 'fa-brands fa-meta', label: key };
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
        // Change 4: auto-show platform column when 2+ platforms are connected
        if (this.connectedPlatformCount >= 2 && !this.platformColumnVisible) {
          this.platformColumnVisible = true;
        }
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
      this.savedViews = [];
      this.activeSavedView = null;
      this.savedViewsModified = false;
    }
  }

  private initDateRange(): void {
    this.applyDateSelectionState(DEFAULT_DATE_SELECTION);
  }

  private buildDateSelectionFromState(): DateRangeSelection {
    return {
      preset: this.activePresetKey,
      dateStart: this.activePresetKey ? null : (this.dateStart || null),
      dateStop: this.activePresetKey ? null : (this.dateStop || null),
      compareToPrevious: this.dateSelection.compareToPrevious ?? false,
    };
  }

  private applyDateSelectionState(selection: DateRangeSelection): void {
    const normalized = this.normalizeDateSelection(selection);
    const resolved = this.resolveSelectionDates(normalized);
    this.dateSelection = normalized;
    this.activePresetKey = normalized.preset;
    this.activePreset = normalized.preset ? (ROLLING_PRESET_DAYS[normalized.preset] ?? null) : null;
    this.dateStart = resolved.dateStart;
    this.dateStop = resolved.dateStop;
    this.dateRangeSelected = !!this.dateStart && !!this.dateStop;
  }

  private normalizeDateSelection(selection: DateRangeSelection | null | undefined): DateRangeSelection {
    const next = selection ?? DEFAULT_DATE_SELECTION;
    return {
      preset: next.preset ?? null,
      dateStart: next.preset ? null : (next.dateStart ?? null),
      dateStop: next.preset ? null : (next.dateStop ?? null),
      compareToPrevious: next.compareToPrevious ?? false,
    };
  }

  private sameDateSelection(a: DateRangeSelection, b: DateRangeSelection): boolean {
    return a.preset === b.preset
      && a.dateStart === b.dateStart
      && a.dateStop === b.dateStop
      && a.compareToPrevious === b.compareToPrevious;
  }

  private resolveSelectionDates(selection: DateRangeSelection): { dateStart: string; dateStop: string } {
    if (selection.preset) {
      return this.resolvePresetDates(selection.preset);
    }

    return {
      dateStart: selection.dateStart ?? this.dateStart,
      dateStop: selection.dateStop ?? this.dateStop,
    };
  }

  private resolvePresetDates(preset: DatePresetId): { dateStart: string; dateStop: string } {
    const today = new Date();
    let start = new Date(today);
    let stop = new Date(today);

    switch (preset) {
      case 'today':
        break;
      case 'yesterday':
        start.setDate(today.getDate() - 1);
        stop = new Date(start);
        break;
      case 'last_7d':
      case 'last_14d':
      case 'last_30d':
      case 'last_90d':
        start.setDate(today.getDate() - (ROLLING_PRESET_DAYS[preset] ?? 30));
        break;
      case 'this_week': {
        const day = today.getDay();
        const diff = day === 0 ? -6 : 1 - day;
        start.setDate(today.getDate() + diff);
        break;
      }
      case 'last_week_mon_sun': {
        const day = today.getDay();
        const daysSinceMonday = day === 0 ? 6 : day - 1;
        const thisWeekMonday = new Date(today);
        thisWeekMonday.setDate(today.getDate() - daysSinceMonday);
        start = new Date(thisWeekMonday);
        start.setDate(thisWeekMonday.getDate() - 7);
        stop = new Date(start);
        stop.setDate(start.getDate() + 6);
        break;
      }
      case 'this_month':
        start = new Date(today.getFullYear(), today.getMonth(), 1);
        break;
      case 'last_month':
        start = new Date(today.getFullYear(), today.getMonth() - 1, 1);
        stop = new Date(today.getFullYear(), today.getMonth(), 0);
        break;
      case 'this_year':
        start = new Date(today.getFullYear(), 0, 1);
        break;
      case 'maximum':
        start = new Date(2019, 0, 1);
        break;
      default:
        start.setDate(today.getDate() - 30);
        break;
    }

    return {
      dateStart: this.formatDate(start),
      dateStop: this.formatDate(stop),
    };
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
    // Auto-fetch the active tab's data from the DB on first visit
    this.fetchTabData(this.activeTabIndex);
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
      case 1: {
        const filter = this.drillFilteredAdSetExternalIds;
        return filter
          ? this.adSetInsights.filter(s => filter.has(s.objectExternalId ?? ''))
          : this.adSetInsights;
      }
      case 2: {
        const filter = this.drillFilteredAdExternalIds;
        return filter
          ? this.adInsights.filter(s => filter.has(s.objectExternalId ?? ''))
          : this.adInsights;
      }
      default:
        return this.campaignInsights;
    }
  }

  private get drillFilteredAdSetExternalIds(): Set<string> | null {
    if (this.selectedCampaignIds.size === 0) return null;
    const campaignDbIds = new Set(
      this.availableCampaigns
        .filter(c => c.externalId != null && this.selectedCampaignIds.has(c.externalId))
        .map(c => c.id as number),
    );
    return new Set(
      this.availableAdSets
        .filter(a => a.campaignId != null && campaignDbIds.has(a.campaignId) && a.externalId != null)
        .map(a => a.externalId as string),
    );
  }

  private get drillFilteredAdExternalIds(): Set<string> | null {
    if (this.selectedAdSetIds.size === 0 && this.selectedCampaignIds.size === 0) return null;
    let adSetDbIds: Set<number>;
    if (this.selectedAdSetIds.size > 0) {
      adSetDbIds = new Set(
        this.availableAdSets
          .filter(a => a.externalId != null && this.selectedAdSetIds.has(a.externalId))
          .map(a => a.id),
      );
    } else {
      const campaignDbIds = new Set(
        this.availableCampaigns
          .filter(c => c.externalId != null && this.selectedCampaignIds.has(c.externalId))
          .map(c => c.id as number),
      );
      adSetDbIds = new Set(
        this.availableAdSets
          .filter(a => a.campaignId != null && campaignDbIds.has(a.campaignId))
          .map(a => a.id),
      );
    }
    return new Set(
      this.availableAds
        .filter(a => a.adSetId != null && adSetDbIds.has(a.adSetId) && a.externalId != null)
        .map(a => a.externalId as string),
    );
  }

  onTabChange(tabIndex: number): void {
    this.activeTabIndex = tabIndex;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: tabIndex },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.saveLastView();
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
      if (this.dateSelection.compareToPrevious && prevSnaps && block.metricKey) {
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
    this.saveLastView();
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

  onDateSelectionChange(selection: DateRangeSelection): void {
    const normalized = this.normalizeDateSelection(selection);
    const previous = this.buildDateSelectionFromState();
    if (this.sameDateSelection(previous, normalized)) {
      return;
    }

    this.applyDateSelectionState(normalized);
    const datesChanged = previous.preset !== normalized.preset
      || previous.dateStart !== normalized.dateStart
      || previous.dateStop !== normalized.dateStop;

    if (datesChanged) {
      this.onDateRangeChange();
    } else {
      this.refreshCurrentView();
    }

    this.saveLastView();
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

  drawGraph(): void {
    const canvas = this.graphCanvasRef?.nativeElement;
    if (!canvas) return;
    if (this.chartInstance) {
      this.chartInstance.destroy();
      this.chartInstance = null;
    }

    // Change 3: use selectedTreeRow entity data when one row is highlighted
    const snapshots = this.getGraphSnapshots();
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

  // ---------- Drill-down / Breadcrumb ----------

  get breadcrumbVisible(): boolean {
    return this.selectedCampaignIds.size > 0 || this.selectedAdSetIds.size > 0;
  }

  get breadcrumbCampaignName(): string | null {
    if (this.selectedCampaignIds.size === 1) {
      const id = Array.from(this.selectedCampaignIds)[0];
      return this.availableCampaigns.find(c => c.externalId === id)?.name ?? id;
    }
    return null;
  }

  get breadcrumbAdSetName(): string | null {
    if (this.selectedAdSetIds.size === 1 && this.activeTabIndex === 2) {
      const id = Array.from(this.selectedAdSetIds)[0];
      return this.availableAdSets.find(a => a.externalId === id)?.name ?? id;
    }
    return null;
  }

  drillIntoCampaign(c: Campaign): void {
    this.selectedCampaignIds.clear();
    if (c.externalId) this.selectedCampaignIds.add(c.externalId);
    this.selectedAdSetIds.clear();
    this.selectedAdIds.clear();
    this.onTabChange(1);
  }

  drillIntoAdSet(a: AdSetResponse): void {
    this.selectedAdSetIds.clear();
    if (a.externalId) this.selectedAdSetIds.add(a.externalId);
    this.selectedAdIds.clear();
    this.onTabChange(2);
  }

  breadcrumbNavigateHome(): void {
    this.selectedCampaignIds.clear();
    this.selectedAdSetIds.clear();
    this.selectedAdIds.clear();
    this.onTabChange(0);
  }

  breadcrumbNavigateToCampaign(): void {
    this.selectedAdSetIds.clear();
    this.selectedAdIds.clear();
    this.onTabChange(1);
  }

  // ---------- localStorage persistence ----------

  private saveLastView(): void {
    if (this.activeSavedView) this.savedViewsModified = true;
    try {
      const view = {
        dateStart: this.dateStart,
        dateStop: this.dateStop,
        activePreset: this.activePreset,
        activePresetKey: this.activePresetKey,
        compareToPrevious: this.dateSelection.compareToPrevious,
        activeTabIndex: this.activeTabIndex,
        selectedCampaignIds: Array.from(this.selectedCampaignIds),
        selectedAdSetIds: Array.from(this.selectedAdSetIds),
        selectedAdIds: Array.from(this.selectedAdIds),
        activePlatform: this.activePlatform,
        treeLevel: this.treeLevel,
        selectedTreeRow: this.selectedTreeRow,
        expandedNodeIds: [
          ...Array.from(this.expandedCampaignIds).map(id => `c:${id}`),
          ...Array.from(this.expandedAdSetIds).map(id => `a:${id}`),
        ],
        platformColumnVisible: this.platformColumnVisible,
        groupBy: this.groupBy,
        sideBySideMode: this.sideBySideMode,
        selectedPlatforms: this.selectedPlatforms,
        sortBy: this.sortBy,
      };
      localStorage.setItem(InsightsComponent.LAST_VIEW_KEY, JSON.stringify(view));
    } catch { /* quota exceeded or SSR */ }
  }

  private restoreLastView(): boolean {
    try {
      const raw = localStorage.getItem(InsightsComponent.LAST_VIEW_KEY);
      if (!raw) return false;
      const view = JSON.parse(raw);
      if (view.dateStart) this.dateStart = view.dateStart;
      if (view.dateStop) this.dateStop = view.dateStop;
      if (view.activePreset !== undefined) this.activePreset = view.activePreset;
      if (view.activePresetKey !== undefined) this.activePresetKey = view.activePresetKey;
      this.dateSelection = this.normalizeDateSelection({
        preset: view.activePresetKey ?? null,
        dateStart: view.dateStart ?? null,
        dateStop: view.dateStop ?? null,
        compareToPrevious: view.compareToPrevious ?? false,
      });
      this.applyDateSelectionState(this.dateSelection);
      if (view.activeTabIndex !== undefined) this.activeTabIndex = view.activeTabIndex;
      if (Array.isArray(view.selectedCampaignIds)) this.selectedCampaignIds = new Set(view.selectedCampaignIds);
      if (Array.isArray(view.selectedAdSetIds)) this.selectedAdSetIds = new Set(view.selectedAdSetIds);
      if (Array.isArray(view.selectedAdIds)) this.selectedAdIds = new Set(view.selectedAdIds);
      if (view.activePlatform) this.activePlatform = view.activePlatform;
      // New fields — safe defaults for older stored views
      if (view.treeLevel) this.treeLevel = view.treeLevel;
      if (view.selectedTreeRow !== undefined) this.selectedTreeRow = view.selectedTreeRow;
      if (Array.isArray(view.expandedNodeIds)) {
        this.expandedCampaignIds = new Set(
          view.expandedNodeIds.filter((id: string) => id.startsWith('c:')).map((id: string) => id.slice(2))
        );
        this.expandedAdSetIds = new Set(
          view.expandedNodeIds.filter((id: string) => id.startsWith('a:')).map((id: string) => id.slice(2))
        );
      }
      if (view.platformColumnVisible !== undefined) this.platformColumnVisible = view.platformColumnVisible;
      if (view.groupBy) this.groupBy = view.groupBy;
      if (view.sideBySideMode !== undefined) this.sideBySideMode = view.sideBySideMode;
      if (Array.isArray(view.selectedPlatforms) && view.selectedPlatforms.length) {
        this.selectedPlatforms = view.selectedPlatforms;
      }
      if (view.sortBy !== undefined) this.sortBy = view.sortBy;
      this.dateRangeSelected = true;
      return true;
    } catch { return false; }
  }

  // ---------- Saved Views ----------

  loadSavedViews(): void {
    this.savedViewsLoading = true;
    this.savedViewService.list(this.activePlatform).pipe(
      finalize(() => { this.savedViewsLoading = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (views) => { this.savedViews = views ?? []; },
      error: () => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error('Failed to load saved views');
        }
      },
    });
  }

  toggleSavedViewsDropdown(): void {
    this.savedViewsDropdownOpen = !this.savedViewsDropdownOpen;
    if (this.savedViewsDropdownOpen) {
      this.loadSavedViews();
    }
  }

  closeSavedViewsDropdown(): void {
    this.savedViewsDropdownOpen = false;
    this.svMenuViewId = null;
  }

  applySavedView(view: InsightsSavedView): void {
    const cfg = view.viewConfig;
    if (!cfg) return;
    this.applyDateSelectionState({
      preset: (cfg.activePresetKey as DatePresetId | null) ?? null,
      dateStart: cfg.dateStart ?? null,
      dateStop: cfg.dateStop ?? null,
      compareToPrevious: cfg.compareToPrevious ?? false,
    });
    if (cfg.activeTabIndex !== undefined) this.activeTabIndex = cfg.activeTabIndex;
    this.selectedCampaignIds = new Set(cfg.selectedCampaignIds ?? []);
    this.selectedAdSetIds = new Set(cfg.selectedAdSetIds ?? []);
    this.selectedAdIds = new Set(cfg.selectedAdIds ?? []);
    if (cfg.activePlatform) this.activePlatform = cfg.activePlatform;
    if (Array.isArray(cfg.kpiCardMetrics) && cfg.kpiCardMetrics.length === 6) {
      this.metricBlocks = cfg.kpiCardMetrics.map((key, i) => ({
        index: i,
        metricKey: key || null,
        label: key ? this.formatMetricLabel(key) : 'Click to select',
        value: '—',
        icon: key ? (METRIC_ICONS[key] ?? 'analytics') : 'add_circle',
      }));
    }
    // Restore new fields — fall back to defaults when missing (older saved views)
    this.treeLevel = cfg.treeLevel ?? 'all';
    this.selectedTreeRow = cfg.selectedTreeRow ?? null;
    if (Array.isArray(cfg.expandedNodeIds)) {
      this.expandedCampaignIds = new Set(
        cfg.expandedNodeIds.filter((id: string) => id.startsWith('c:')).map((id: string) => id.slice(2))
      );
      this.expandedAdSetIds = new Set(
        cfg.expandedNodeIds.filter((id: string) => id.startsWith('a:')).map((id: string) => id.slice(2))
      );
    }
    if (cfg.visibleMetrics) {
      this.visibleMetrics = {
        campaigns: Array.isArray(cfg.visibleMetrics.campaigns) && cfg.visibleMetrics.campaigns.length
          ? cfg.visibleMetrics.campaigns : [...DEFAULT_VISIBLE_METRICS],
        adSets: Array.isArray(cfg.visibleMetrics.adSets) && cfg.visibleMetrics.adSets.length
          ? cfg.visibleMetrics.adSets : [...DEFAULT_VISIBLE_METRICS],
        ads: Array.isArray(cfg.visibleMetrics.ads) && cfg.visibleMetrics.ads.length
          ? cfg.visibleMetrics.ads : [...DEFAULT_VISIBLE_METRICS],
      };
    }
    if (cfg.platformColumnVisible !== undefined) this.platformColumnVisible = cfg.platformColumnVisible;
    this.groupBy = cfg.groupBy ?? 'none';
    this.sideBySideMode = cfg.sideBySideMode ?? false;
    if (Array.isArray(cfg.selectedPlatforms) && cfg.selectedPlatforms.length) {
      this.selectedPlatforms = cfg.selectedPlatforms;
    }
    this.sortBy = cfg.sortBy ?? null;

    this.dateRangeSelected = true;
    this.activeSavedView = view;
    this.savedViewsModified = false;
    this.savedViewsDropdownOpen = false;
    this.clearCache();
    this.loadAllData();
    this.saveLastView();
    this.cdr.detectChanges();
  }

  get savedViewsButtonLabel(): string {
    if (!this.activeSavedView) return 'Saved views';
    const suffix = this.savedViewsModified ? ' •' : '';
    return `Saved views: ${this.activeSavedView.name}${suffix}`;
  }

  openSaveDialog(): void {
    this.saveDialogOpen = true;
    this.saveDialogName = '';
    this.saveDialogDescription = '';
    this.saveDialogPin = false;
    this.saveDialogError = '';
    this.savedViewsDropdownOpen = false;
  }

  closeSaveDialog(): void {
    this.saveDialogOpen = false;
  }

  submitSaveDialog(): void {
    if (!this.saveDialogName.trim()) {
      this.saveDialogError = 'Name is required.';
      return;
    }
    this.saveDialogSaving = true;
    this.saveDialogError = '';
    const config = this.buildViewConfig();
    const dto: Partial<InsightsSavedView> = {
      name: this.saveDialogName.trim(),
      description: this.saveDialogDescription.trim() || undefined,
      provider: this.activePlatform as InsightsSavedView['provider'],
      viewConfig: config,
      pinned: this.saveDialogPin,
    };
    this.savedViewService.createView(dto).pipe(
      finalize(() => { this.saveDialogSaving = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (view) => {
        this.activeSavedView = view;
        this.savedViewsModified = false;
        this.saveDialogOpen = false;
        this.toastr.success('View saved');
        this.loadSavedViews();
      },
      error: (err) => {
        this.saveDialogError = err?.error?.error ?? 'Failed to save view.';
      },
    });
  }

  openManageDrawer(): void {
    this.manageDrawerOpen = true;
    this.savedViewsDropdownOpen = false;
    this.manageDrawerLoading = true;
    this.savedViewService.list(this.activePlatform).pipe(
      finalize(() => { this.manageDrawerLoading = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (views) => { this.manageDrawerViews = views ?? []; },
      error: () => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error('Failed to load saved views');
        }
      },
    });
  }

  closeManageDrawer(): void {
    this.manageDrawerOpen = false;
  }

  deleteView(view: InsightsSavedView): void {
    this.savedViewService.delete(view.id).subscribe({
      next: () => {
        this.manageDrawerViews = this.manageDrawerViews.filter(v => v.id !== view.id);
        this.savedViews = this.savedViews.filter(v => v.id !== view.id);
        if (this.activeSavedView?.id === view.id) {
          this.activeSavedView = null;
          this.savedViewsModified = false;
        }
        this.cdr.detectChanges();
      },
      error: () => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error('Failed to delete view');
        }
      },
    });
  }

  // Three-dot menu per row
  toggleSvMenu(viewId: number, event: MouseEvent): void {
    event.stopPropagation();
    this.svMenuViewId = this.svMenuViewId === viewId ? null : viewId;
  }

  closeSvMenu(): void {
    this.svMenuViewId = null;
  }

  updateActiveSavedView(view: InsightsSavedView): void {
    this.closeSvMenu();
    this.savedViewsDropdownOpen = false;
    const dto: Partial<InsightsSavedView> = {
      name: view.name,
      description: view.description,
      provider: view.provider,
      pinned: view.pinned,
      viewConfig: this.buildViewConfig(),
    };
    this.savedViewService.update(view.id, dto).subscribe({
      next: (updated) => {
        this.activeSavedView = updated;
        this.savedViewsModified = false;
        this.toastr.success('View updated');
        this.loadSavedViews();
        this.cdr.detectChanges();
      },
      error: (err) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          const msg = err?.error?.message ?? err?.error?.error ?? err?.message ?? 'Failed to update view';
          this.toastr.error(msg);
        }
      },
    });
  }

  // Delete confirmation
  requestDeleteView(view: InsightsSavedView, event: MouseEvent): void {
    event.stopPropagation();
    this.svMenuViewId = null;
    this.deleteConfirmView = view;
    this.deleteConfirmOpen = true;
  }

  confirmDeleteView(): void {
    if (!this.deleteConfirmView) return;
    const view = this.deleteConfirmView;
    this.deleteConfirmOpen = false;
    this.deleteConfirmView = null;
    this.deleteView(view);
  }

  cancelDeleteView(): void {
    this.deleteConfirmOpen = false;
    this.deleteConfirmView = null;
  }

  // Rename dialog
  openRenameDialog(view: InsightsSavedView, event: MouseEvent): void {
    event.stopPropagation();
    this.svMenuViewId = null;
    this.renameDialogView = view;
    this.renameDialogName = view.name;
    this.renameDialogError = '';
    this.renameDialogOpen = true;
  }

  closeRenameDialog(): void {
    this.renameDialogOpen = false;
    this.renameDialogView = null;
    this.renameDialogName = '';
    this.renameDialogError = '';
  }

  submitRenameDialog(): void {
    if (!this.renameDialogName.trim()) {
      this.renameDialogError = 'Name is required.';
      return;
    }
    if (!this.renameDialogView) return;
    this.renameDialogSaving = true;
    this.renameDialogError = '';
    this.savedViewService.update(this.renameDialogView.id, { name: this.renameDialogName.trim() }).pipe(
      finalize(() => { this.renameDialogSaving = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (updated) => {
        const updateList = (list: InsightsSavedView[]) => list.map(v => v.id === updated.id ? updated : v);
        this.savedViews = updateList(this.savedViews);
        this.manageDrawerViews = updateList(this.manageDrawerViews);
        if (this.activeSavedView?.id === updated.id) this.activeSavedView = updated;
        this.renameDialogOpen = false;
        this.renameDialogView = null;
      },
      error: (err) => {
        this.renameDialogError = err?.error?.error ?? 'Failed to rename view.';
      },
    });
  }

  togglePinView(view: InsightsSavedView): void {
    this.savedViewService.togglePin(view.id).subscribe({
      next: (updated) => {
        const updateList = (list: InsightsSavedView[]) =>
          list.map(v => v.id === updated.id ? updated : v)
            .sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));
        this.manageDrawerViews = updateList(this.manageDrawerViews);
        this.savedViews = updateList(this.savedViews);
        if (this.activeSavedView?.id === updated.id) this.activeSavedView = updated;
        this.cdr.detectChanges();
      },
      error: () => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error('Failed to update view');
        }
      },
    });
  }

  private buildViewConfig(): InsightsViewConfig {
    return {
      datePreset: this.activePresetKey,
      dateStart: this.dateStart,
      dateStop: this.dateStop,
      activePreset: this.activePreset,
      activePresetKey: this.activePresetKey,
      compareToPrevious: this.dateSelection.compareToPrevious,
      activeTabIndex: this.activeTabIndex,
      selectedCampaignIds: Array.from(this.selectedCampaignIds),
      selectedAdSetIds: Array.from(this.selectedAdSetIds),
      selectedAdIds: Array.from(this.selectedAdIds),
      activePlatform: this.activePlatform,
      kpiCardMetrics: this.metricBlocks.map(b => b.metricKey ?? ''),
      // Change 1
      treeLevel: this.treeLevel,
      selectedTreeRow: this.selectedTreeRow,
      expandedNodeIds: [
        ...Array.from(this.expandedCampaignIds).map(id => `c:${id}`),
        ...Array.from(this.expandedAdSetIds).map(id => `a:${id}`),
      ],
      // Change 2
      visibleMetrics: {
        campaigns: [...this.visibleMetrics.campaigns],
        adSets: [...this.visibleMetrics.adSets],
        ads: [...this.visibleMetrics.ads],
      },
      // Change 4
      platformColumnVisible: this.platformColumnVisible,
      groupBy: this.groupBy,
      sideBySideMode: this.sideBySideMode,
      selectedPlatforms: [...this.selectedPlatforms],
      // Change 5
      sortBy: this.sortBy,
    };
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

  // ---------- Change 1 — Tree level + row selection ----------

  setTreeLevel(level: 'all' | 'campaigns' | 'adSets' | 'ads'): void {
    const prevLevel = this.treeLevel;
    this.treeLevel = level;

    // Drive activeTabIndex from treeLevel so existing data-loading logic works
    const tabMap: Record<typeof level, number> = { all: 0, campaigns: 0, adSets: 1, ads: 2 };
    const newTab = tabMap[level];
    if (level !== 'all' && newTab !== this.activeTabIndex) {
      this.activeTabIndex = newTab;
      if (!this.tabDataLoaded[newTab]) {
        this.fetchTabData(newTab);
      } else {
        this.refreshCurrentView();
      }
    } else if (level === 'all') {
      // Ensure all three data sets are loaded
      [0, 1, 2].forEach(t => { if (!this.tabDataLoaded[t]) this.fetchTabData(t); });
    }

    // One-time toast when switching to 'all' if some chips would disappear from intersection
    if (level === 'all' && prevLevel !== 'all' && !this.metricsIntersectionToastShown) {
      const prevSet = prevLevel === 'campaigns' ? this.visibleMetrics.campaigns
                    : prevLevel === 'adSets' ? this.visibleMetrics.adSets
                    : this.visibleMetrics.ads;
      const intersection = this.currentVisibleMetrics;
      if (intersection.length < prevSet.length) {
        this.metricsIntersectionToastShown = true;
        this.toastr.info('Some metrics hidden — not available at all levels.');
      }
    }

    this.saveLastView();
  }

  onTreeRowBodyClick(objectType: 'CAMPAIGN' | 'ADSET' | 'AD', externalId: string, event: MouseEvent): void {
    const target = event.target as HTMLElement;
    // Let checkbox, expand button, and their children handle their own events
    if (
      target.tagName === 'INPUT' ||
      target.closest('.sel-expand-btn') ||
      target.closest('.chip-remove-btn')
    ) return;

    // Campaigns support multi-select via row click
    if (objectType === 'CAMPAIGN') {
      const campaign = this.availableCampaigns.find(c => c.externalId === externalId);
      if (campaign) this.toggleCampaign(campaign);
    }

    if (
      this.selectedTreeRow?.objectExternalId === externalId &&
      this.selectedTreeRow?.objectType === objectType
    ) {
      this.clearTreeRowSelection();
    } else {
      this.selectedTreeRow = { objectType, objectExternalId: externalId };
      this.saveLastView();
      setTimeout(() => this.drawGraph(), 50);
    }
  }

  clearTreeRowSelection(): void {
    this.selectedTreeRow = null;
    this.saveLastView();
    setTimeout(() => this.drawGraph(), 50);
  }

  isRowHighlighted(objectType: 'CAMPAIGN' | 'ADSET' | 'AD', externalId: string | undefined | null): boolean {
    if (!this.selectedTreeRow || !externalId) return false;
    return this.selectedTreeRow.objectType === objectType &&
           this.selectedTreeRow.objectExternalId === externalId;
  }

  // ---------- Change 2 — Visible metric chips ----------

  removeVisibleMetric(metric: string): void {
    const apply = (arr: string[]) => arr.filter(m => m !== metric);
    if (this.treeLevel === 'all') {
      this.visibleMetrics = {
        campaigns: apply(this.visibleMetrics.campaigns),
        adSets: apply(this.visibleMetrics.adSets),
        ads: apply(this.visibleMetrics.ads),
      };
    } else if (this.treeLevel === 'campaigns') {
      this.visibleMetrics = { ...this.visibleMetrics, campaigns: apply(this.visibleMetrics.campaigns) };
    } else if (this.treeLevel === 'adSets') {
      this.visibleMetrics = { ...this.visibleMetrics, adSets: apply(this.visibleMetrics.adSets) };
    } else {
      this.visibleMetrics = { ...this.visibleMetrics, ads: apply(this.visibleMetrics.ads) };
    }
    this.saveLastView();
  }

  toggleVisibleMetric(metric: string): void {
    if (this.isMetricVisible(metric)) {
      this.removeVisibleMetric(metric);
      return;
    }
    const add = (arr: string[]) => arr.includes(metric) ? arr : [...arr, metric];
    if (this.treeLevel === 'all') {
      this.visibleMetrics = {
        campaigns: add(this.visibleMetrics.campaigns),
        adSets: add(this.visibleMetrics.adSets),
        ads: add(this.visibleMetrics.ads),
      };
    } else if (this.treeLevel === 'campaigns') {
      this.visibleMetrics = { ...this.visibleMetrics, campaigns: add(this.visibleMetrics.campaigns) };
    } else if (this.treeLevel === 'adSets') {
      this.visibleMetrics = { ...this.visibleMetrics, adSets: add(this.visibleMetrics.adSets) };
    } else {
      this.visibleMetrics = { ...this.visibleMetrics, ads: add(this.visibleMetrics.ads) };
    }
    this.saveLastView();
  }

  isMetricVisible(metric: string): boolean {
    return this.currentVisibleMetrics.includes(metric);
  }

  toggleMetricDropdown(): void {
    this.metricDropdownOpen = !this.metricDropdownOpen;
    if (this.metricDropdownOpen) this.metricDropdownSearch = '';
  }

  closeMetricDropdown(): void {
    if (this.metricDropdownOpen) {
      this.metricDropdownOpen = false;
      this.cdr.detectChanges();
    }
  }

  getEntityMetricValue(entityType: 'campaign' | 'adset' | 'ad', externalId: string, metricKey: string): string {
    if (!externalId) return '—';
    const pool = entityType === 'campaign' ? this.campaignInsights
               : entityType === 'adset' ? this.adSetInsights
               : this.adInsights;
    const snaps = pool.filter(s => s.objectExternalId === externalId);
    if (!snaps.length) return '—';
    return this.aggregateMetric(metricKey, snaps);
  }

  // ---------- Change 4 — Platform column + side-by-side ----------

  togglePlatformColumn(): void {
    this.platformColumnVisible = !this.platformColumnVisible;
    this.saveLastView();
  }

  toggleSideBySide(): void {
    if (!this.isSideBySideAvailable) return;
    this.sideBySideMode = !this.sideBySideMode;
    this.saveLastView();
    this.cdr.detectChanges();
  }

  // ---------- Change 5 — Sort + bulk export ----------

  toggleSortBy(column: string): void {
    if (this.sortBy?.column === column) {
      if (this.sortBy.direction === 'desc') {
        this.sortBy = { column, direction: 'asc' };
      } else {
        // third click clears sort
        this.sortBy = null;
      }
    } else {
      this.sortBy = { column, direction: 'desc' };
    }
    this.saveLastView();
  }

  exportCsv(): void {
    const metrics = this.currentVisibleMetrics;
    const header = ['Name', 'External ID', 'Type', ...metrics.map(m => this.formatMetricLabel(m))];

    const buildRow = (name: string, extId: string, type: string, etype: 'campaign' | 'adset' | 'ad') =>
      [name, extId, type, ...metrics.map(m => this.getEntityMetricValue(etype, extId, m))];

    const rows: string[][] = [];
    if (this.treeLevel === 'all' || this.treeLevel === 'campaigns') {
      for (const c of this.sortedCampaignRows) {
        rows.push(buildRow(c.name ?? '', c.externalId ?? '', 'Campaign', 'campaign'));
      }
    }
    if (this.treeLevel === 'adSets') {
      for (const a of this.sortedAdSetRows) {
        rows.push(buildRow(a.name ?? '', a.externalId ?? '', 'Ad Set', 'adset'));
      }
    }
    if (this.treeLevel === 'ads') {
      for (const a of this.sortedAdRows) {
        rows.push(buildRow(a.name ?? '', a.externalId ?? '', 'Ad', 'ad'));
      }
    }

    const escape = (s: string) => `"${s.replace(/"/g, '""')}"`;
    const csv = [header, ...rows].map(r => r.map(escape).join(',')).join('\n');
    if (typeof window === 'undefined') return;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `insights-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ---------- Helpers ----------

  private formatDate(d: Date): string {
    const year = d.getFullYear();
    const month = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
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








