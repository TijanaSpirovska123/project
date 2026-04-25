import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { finalize } from 'rxjs/operators';
import {
  ColumnDef,
  CAMPAIGN_COLUMNS,
  ADSET_COLUMNS,
  AD_COLUMNS,
} from '../../data/meta-column-config';
import { META_STANDARD_EVENTS } from '../../data/meta-standard-events';
import { deepClone } from '../../utils/deep-clone.util';

import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { AdSetService } from '../../services/adset/adset.service';
import { AdService } from '../../services/ad/ad.service';
import { CoreService } from '../../services/core/core.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { UserConfigService } from '../../services/user-config/user-config.service';
import { Provider } from '../../data/provider/provider.enum';
import { CampaignStatus } from '../../data/campaign/campaign-status';
import { Campaign } from '../../models/campaign/campaign';
import { AdSetResponse, AdResponse } from '../../models/adset/adset.model';
import { TableData } from '../../data/table/table-data.model';
import { TableHeader } from '../../data/table/table-header.model';
import { TableButton } from '../../data/table/table-button.model';
import {
  AnalyticsCard,
  CampaignMetrics,
  DeviceBreakdown,
  AgeBreakdown,
  PerformanceData,
} from '../../models/analytics/analytics.model';

@Component({
  selector: 'app-meta',
  standalone: false,
  templateUrl: './meta.component.html',
  styleUrls: ['./meta.component.scss'],
})
export class MetaComponent implements OnInit {
  formGroup!: FormGroup;
  userId: string = '';
  actId: string | null = null;

  // Raw data from API
  campaigns: Campaign[] = [];

  // Table data per tab
  campaignTableData: TableData[] = [];
  adSetTableData: TableData[] = [];
  adTableData: TableData[] = [];

  // Headers per tab
  campaignHeaders: TableHeader[] = [];
  adSetHeaders: TableHeader[] = [];
  adHeaders: TableHeader[] = [];

  buttons: TableButton[] = [];

  // Tabs
  tabs: string[] = ['Campaigns', 'Ad Sets', 'Ads'];
  activeTabIndex: number = 0;

  // Column configs (mutable per-tab)
  campaignColumnConfig: ColumnDef[] = [];
  adSetColumnConfig: ColumnDef[] = [];
  adColumnConfig: ColumnDef[] = [];

  // Column customiser state
  isColumnCustomiserOpen = false;
  customiserCategoryIndex = 0;
  columnSearch = '';
  tempColumnConfig: ColumnDef[] = [];
  collapsedSections: Set<string> = new Set();

  readonly customiserCategories = [
    { key: 'key_metrics', label: 'Key metrics' },
    { key: 'tracking', label: 'Tracking' },
    { key: 'ad_settings', label: 'Ad settings' },
    { key: 'advanced', label: 'Advanced' },
    { key: 'custom', label: 'Custom' },
  ];

  readonly seList = META_STANDARD_EVENTS;

  readonly kmSections: {
    id: string;
    label: string;
    keys: string[];
    isConversionTable?: boolean;
  }[] = [
    {
      id: 'results',
      label: 'Results',
      keys: [
        'results',
        'cost_per_result',
        'result_rate',
        'results_roas',
        'results_value',
      ],
    },
    {
      id: 'spend',
      label: 'Spend',
      keys: ['spend', 'spend_pct'],
    },
    {
      id: 'impressions',
      label: 'Impressions',
      keys: ['impressions', 'reach', 'frequency', 'cpm', 'cpp'],
    },
    {
      id: 'views',
      label: 'Views',
      keys: ['viewers', 'views'],
    },
    {
      id: 'media',
      label: 'Media',
      keys: [
        'video_plays_2s',
        'video_plays_3s',
        'cost_per_2s_video_play',
        'cost_per_3s_video_play',
        'video_plays',
        'thruplay_views',
        'cost_per_thruplay',
        'unique_2s_video_plays',
      ],
    },
    {
      id: 'clicks',
      label: 'Clicks',
      keys: [
        'clicks_all',
        'cpc_all',
        'cpc_link',
        'ctr_all',
        'ctr_link',
        'cost_per_unique_click',
        'cost_per_unique_link_click',
        'cost_per_outbound_click',
        'cost_per_unique_outbound',
        'link_clicks',
        'outbound_clicks',
        'outbound_ctr',
        'photo_clicks',
        'shop_clicks',
        'unique_clicks_all',
        'unique_ctr_all',
        'unique_ctr_link',
        'unique_link_clicks',
        'unique_outbound_clicks',
        'unique_outbound_ctr',
      ],
    },
    {
      id: 'traffic',
      label: 'Traffic',
      keys: [
        'landing_page_views',
        'cost_per_landing_page_view',
        'ig_profile_visits',
      ],
    },
    {
      id: 'follows_likes',
      label: 'Follows & likes',
      keys: ['fb_likes', 'cost_per_like', 'ig_follows'],
    },
    {
      id: 'engagement',
      label: 'Engagement',
      keys: [
        'post_engagements',
        'page_engagement',
        'cost_per_post_engagement',
        'cost_per_page_engagement',
        'post_reactions',
        'post_comments',
        'post_shares',
        'post_saves',
        'check_ins',
        'event_responses',
        'cost_per_event_response',
        'join_group_requests',
        'cost_per_join_group',
        'effect_share',
        'net_reminders_on',
      ],
    },
    {
      id: 'messaging',
      label: 'Messaging',
      keys: [
        'msg_conversations_started',
        'cost_per_msg_convo',
        'msg_conversations_replied',
        'msg_contacts',
        'new_msg_contacts',
        'cost_per_new_msg_contact',
        'returning_msg_contacts',
        'msg_subscriptions',
        'cost_per_msg_subscription',
        'welcome_msg_views',
      ],
    },
    {
      id: 'calling',
      label: 'Calling',
      keys: [
        'callback_requests',
        'phone_calls_placed',
        'messenger_calls_placed',
        'calls_20s_messenger',
        'calls_20s_phone',
        'calls_60s_messenger',
        'calls_60s_phone',
        'blocks',
      ],
    },
    {
      id: 'standard_events',
      label: 'Standard events',
      keys: META_STANDARD_EVENTS.flatMap((e) => [
        `conv_${e.id}_total`,
        `conv_${e.id}_value`,
        `conv_${e.id}_cost`,
      ]),
      isConversionTable: true,
    },
    {
      id: 'custom_events',
      label: 'Custom events',
      keys: ['custom_events_input'],
    },
  ];

  // Modal
  isModalOpen: boolean = false;
  title: string = '';
  isEdit: boolean = false;

  // Confirmation modal
  isConfirmModalOpen: boolean = false;
  confirmMessage: string = '';
  confirmAction: (() => void) | null = null;

  // Analytics dashboard
  analyticsCards: AnalyticsCard[] = [];
  selectedCampaign: Campaign | null = null;
  activeModalTab: 'form' | 'graph' = 'form';
  selectedTimePeriod: number = 30;
  campaignMetrics: CampaignMetrics | null = null;
  deviceBreakdown: DeviceBreakdown[] = [];
  ageBreakdown: AgeBreakdown[] = [];
  performanceData: PerformanceData[] = [];

  // Top item for analytics
  topItemData: any = null;
  topItemType: 'campaign' | 'adset' | 'ad' | null = null;
  preloadedItemData: any = null;

  search = '';
  isLoading = false;
  private pendingRequests = 0;

  // Expose enums to template
  CampaignStatus = CampaignStatus;
  Provider = Provider;

  isLoadingColumns = false;

  constructor(
    private readonly toastr: AppToastrService,
    private readonly formBuilder: FormBuilder,
    private readonly campaignService: CampaignService,
    private readonly adSetService: AdSetService,
    private readonly adService: AdService,
    private readonly authStore: AuthStoreService,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly userConfigService: UserConfigService,
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
    this.campaignColumnConfig = this.getColumnConfigForTab(0);
    this.adSetColumnConfig = this.getColumnConfigForTab(1);
    this.adColumnConfig = this.getColumnConfigForTab(2);
    this.setCampaignHeaders();
    this.setAdSetHeaders();
    this.setAdHeaders();
    this.setButtons();
    this.createFormGroup();
    this.initializeAnalytics();
    this.loadAllData();
    this.loadColumnConfig(this.activeTabIndex);
  }

  private tabIndexToEntityType(
    tabIndex: number,
  ): 'CAMPAIGN' | 'AD_SET' | 'AD' {
    if (tabIndex === 1) return 'AD_SET';
    if (tabIndex === 2) return 'AD';
    return 'CAMPAIGN';
  }

  private loadColumnConfig(tabIndex: number): void {
    const entityType = this.tabIndexToEntityType(tabIndex);
    this.isLoadingColumns = true;
    this.userConfigService
      .getColumnConfig(entityType)
      .pipe(finalize(() => {
        this.isLoadingColumns = false;
        this.cdr.detectChanges();
      }))
      .subscribe({
        next: (config) => {
          const savedKeys = new Set(config.columns);
          const tabConfig = this.getColumnConfigForTab(tabIndex);
          tabConfig.forEach((col) => {
            col.enabled = col.alwaysVisible || savedKeys.has(col.key);
          });
          switch (tabIndex) {
            case 1:
              this.adSetColumnConfig = tabConfig;
              this.setAdSetHeaders();
              break;
            case 2:
              this.adColumnConfig = tabConfig;
              this.setAdHeaders();
              break;
            default:
              this.campaignColumnConfig = tabConfig;
              this.setCampaignHeaders();
          }
        },
        error: () => {
          // silently fall back to defaults already set in ngOnInit
        },
      });
  }

  // ---------- Data Loading ----------

  loadAllData(): void {
    this.isLoading = true;
    this.pendingRequests = 3;
    this.fetchCampaigns();
    this.fetchAdSets();
    this.fetchAds();
  }

  private finishRequest(): void {
    this.pendingRequests--;
    if (this.pendingRequests === 0) {
      this.isLoading = false;
      this.cdr.detectChanges();
    }
  }

  /**
   * Special column handlers for specific column keys
   * Using Map for O(1) lookup performance with 100+ columns
   */
  private readonly specialColumnHandlers = new Map<
    string,
    (entity: any) => any
  >([
    ['name', (entity) => entity.name || '—'],
    ['status', (entity) => this.statusLabel(entity.status)],
    ['statusBadge', (entity) => this.statusLabel(entity.status)],
    ['platform', (entity) => entity.platform || '—'],
    ['externalId', (entity) => entity.externalId || '—'],
    ['toggle', (entity) => entity.status],
  ]);

  /**
   * Gets value from entity, prioritizing: special handler > entity property > rawData > default
   * Optimized for handling 100+ columns efficiently
   */
  private getColumnValue(key: string, entity: any): any {
    // 1. Check if there's a special handler for this column (O(1) lookup)
    const handler = this.specialColumnHandlers.get(key);
    if (handler) {
      return handler(entity);
    }

    // 2. Check if property exists directly on entity (not null/undefined)
    if (
      entity.hasOwnProperty(key) &&
      entity[key] !== null &&
      entity[key] !== undefined &&
      entity[key] !== ''
    ) {
      return entity[key];
    }

    // 3. Try to get from rawData
    if (entity.rawData && typeof entity.rawData === 'object') {
      const rawValue = entity.rawData[key];
      if (rawValue !== null && rawValue !== undefined && rawValue !== '') {
        return rawValue;
      }
    }

    // 4. Return default
    return '—';
  }

  /**
   * Essential columns that must always be included in table data (for status updates, etc.)
   * even if not enabled for display
   * - externalId: Required for status updates and API calls
   * - platform: Required for determining which service to use
   * - status: Required for toggle button to read current status
   */
  private readonly essentialColumns = ['externalId', 'platform', 'status'];

  /**
   * Populates table rows from entity data and rawData
   * Optimized for 100+ columns - filters once, maps with efficient value extraction
   * Always includes essential columns (externalId, platform) for status updates
   */
  private buildTableRows(
    columnConfig: ColumnDef[],
    entity: any,
  ): Array<{ key: string; value: any }> {
    // Get enabled columns
    const enabledColumns = columnConfig.filter((c) => c.enabled);
    const enabledKeys = new Set(enabledColumns.map((c) => c.key));

    // Build rows for enabled columns
    const rows = enabledColumns.map((c) => ({
      key: c.key,
      value: this.getColumnValue(c.key, entity),
    }));

    // Ensure essential columns are included (for status toggle and other operations)
    // Add them only if not already present in enabled columns
    for (const essentialKey of this.essentialColumns) {
      if (!enabledKeys.has(essentialKey)) {
        rows.push({
          key: essentialKey,
          value: this.getColumnValue(essentialKey, entity),
        });
      }
    }

    return rows;
  }

  fetchCampaigns(): void {
    if (!this.actId) {
      this.toastr.warning('No Meta ad account linked to this user');
      this.finishRequest();
      return;
    }
    this.campaignService.getAllByPlatform(Provider.META).subscribe({
      next: (res) => {
        try {
          this.campaigns = res?.data ?? [];
          this.preloadAnalyticsWithMostRecent();
          this.campaignTableData = this.campaigns.map(
            (c) =>
              ({
                guid: String(c.id),
                expanded: false,
                rows: this.buildTableRows(this.campaignColumnConfig, c),
              }) as TableData,
          );
        } finally {
          this.finishRequest();
        }
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to fetch campaigns'));
        }
        this.finishRequest();
      },
    });
  }

  fetchAdSets(): void {
    if (!this.actId) {
      this.finishRequest();
      return;
    }
    this.adSetService.getAllByPlatform(Provider.META).subscribe({
      next: (res) => {
        try {
          const adSets: AdSetResponse[] = res?.data ?? [];
          this.adSetTableData = adSets.map(
            (as) =>
              ({
                guid: String(as.id),
                expanded: false,
                rows: this.buildTableRows(this.adSetColumnConfig, as),
              }) as TableData,
          );
        } finally {
          this.finishRequest();
        }
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to fetch ad sets'));
        }
        this.finishRequest();
      },
    });
  }

  fetchAds(): void {
    if (!this.actId) {
      this.finishRequest();
      return;
    }
    this.adService.getAllByPlatform(Provider.META).subscribe({
      next: (res) => {
        try {
          const ads: AdResponse[] = res?.data ?? [];
          this.adTableData = ads.map(
            (ad) =>
              ({
                guid: String(ad.id),
                expanded: false,
                rows: this.buildTableRows(this.adColumnConfig, ad),
              }) as TableData,
          );
        } finally {
          this.finishRequest();
        }
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to fetch ads'));
        }
        this.finishRequest();
      },
    });
  }

  // ---------- Tab & Data Helpers ----------

  getCurrentData(): TableData[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetTableData;
      case 2:
        return this.adTableData;
      default:
        return this.campaignTableData;
    }
  }

  getCurrentHeaders(): TableHeader[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetHeaders;
      case 2:
        return this.adHeaders;
      default:
        return this.campaignHeaders;
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
    this.loadColumnConfig(tabIndex);
  }

  // ---------- Headers ----------

  private buildHeaders(config: ColumnDef[]): TableHeader[] {
    return config
      .filter((c) => c.enabled)
      .sort((a, b) => a.order - b.order)
      .map((c) => ({
        title: c.label,
        value: c.key,
        width: c.width,
        appliedSorting: false,
      }));
  }

  setCampaignHeaders(): void {
    this.campaignHeaders = this.buildHeaders(this.campaignColumnConfig);
  }

  setAdSetHeaders(): void {
    this.adSetHeaders = this.buildHeaders(this.adSetColumnConfig);
  }

  setAdHeaders(): void {
    this.adHeaders = this.buildHeaders(this.adColumnConfig);
  }

  setButtons(): void {
    this.buttons = [
      { name: 'Edit', redirect: false },
      { name: 'Delete', redirect: false },
    ];
  }

  // ---------- Status Helper ----------

  private statusLabel(s: string | null | undefined): string {
    const t = (s ?? '').toString().toUpperCase();
    if (t === 'ACTIVE') return 'Active';
    if (t === 'PAUSED') return 'Paused';
    if (t === 'DELETED') return 'Deleted';
    return s ?? '';
  }

  // ---------- Edit / Delete ----------

  onEditClick(event: { item: any; isChild?: boolean; childData?: any }): void {
    if (this.activeTabIndex === 0) {
      this.openModal(event.item.guid);
    } else {
      this.toastr.info('Editing coming soon for this item type');
    }
  }

  onDeleteClick(event: {
    item: any;
    isChild?: boolean;
    childData?: any;
  }): void {
    if (this.activeTabIndex === 0) {
      const campaignName = this.getValue(event.item, 'name') || 'Campaign';
      this.showConfirmModal(
        `Are you sure you want to delete the campaign "${campaignName}"?`,
        () => this.deleteCampaign(event.item.guid),
      );
    } else {
      this.toastr.info('Deletion coming soon for this item type');
    }
  }

  private getValue(item: any, key: string): any {
    return item?.rows?.find((row: any) => row.key === key)?.value;
  }

  // ---------- Confirmation Modal ----------

  showConfirmModal(message: string, action: () => void): void {
    this.confirmMessage = message;
    this.confirmAction = action;
    this.isConfirmModalOpen = true;
  }

  onConfirmYes(): void {
    if (this.confirmAction) {
      this.confirmAction();
    }
    this.closeConfirmModal();
  }

  onConfirmNo(): void {
    this.closeConfirmModal();
  }

  closeConfirmModal(): void {
    this.isConfirmModalOpen = false;
    this.confirmMessage = '';
    this.confirmAction = null;
  }

  // ---------- Status Toggle ----------

  onStatusToggle(event: {
    item: any;
    currentStatus: string;
    newStatus: string;
  }): void {
    const { item, newStatus, currentStatus } = event;

    const platform = item.rows.find((r: any) => r.key === 'platform')?.value;
    const externalId = item.rows.find(
      (r: any) => r.key === 'externalId',
    )?.value;

    if (!platform || !externalId) {
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Missing platform or external ID for this item');
      }
      return;
    }

    // Optimistic update
    const statusRow = item.rows.find((row: any) => row.key === 'status');
    if (statusRow) statusRow.value = newStatus;
    const badgeRow = item.rows.find((row: any) => row.key === 'statusBadge');
    if (badgeRow) badgeRow.value = newStatus;
    const toggleRow = item.rows.find((row: any) => row.key === 'toggle');
    if (toggleRow) toggleRow.value = newStatus;

    const status = newStatus.toUpperCase();
    let request$;
    let entityLabel: string;

    if (this.activeTabIndex === 0) {
      request$ = this.campaignService.patchStatus(platform, externalId, status);
      entityLabel = 'Campaign';
    } else if (this.activeTabIndex === 1) {
      request$ = this.adSetService.patchStatus(platform, externalId, status);
      entityLabel = 'Ad Set';
    } else {
      request$ = this.adService.patchStatus(platform, externalId, status);
      entityLabel = 'Ad';
    }

    item.disabledButtons = true;
    this.cdr.detectChanges();

    request$.pipe(
      finalize(() => { item.disabledButtons = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: (updated: any) => {
        // Sync the returned status back into the table row
        const returnedStatus = updated?.status ?? updated?.data?.status;
        if (returnedStatus) {
          const labeledStatus = this.statusLabel(returnedStatus);
          if (statusRow) statusRow.value = labeledStatus;
          if (badgeRow) badgeRow.value = labeledStatus;
          if (toggleRow) toggleRow.value = labeledStatus;
        }
        this.toastr.success(`${entityLabel} status updated`);
        this.cdr.detectChanges();
      },
      error: (err: any) => {
        // Revert on failure
        if (statusRow) statusRow.value = currentStatus;
        if (badgeRow) badgeRow.value = currentStatus;
        if (toggleRow) toggleRow.value = currentStatus;
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, `Failed to update ${entityLabel} status`));
        }
        this.cdr.detectChanges();
      },
    });
  }

  // ---------- Delete Campaign ----------

  deleteCampaign(guid: any): void {
    this.campaignService.deleteById(guid).subscribe({
      next: () => {
        this.toastr.success('Campaign deleted successfully!');
        this.fetchCampaigns();
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(CoreService.extractErrorMessage(err, 'Failed to delete campaign'));
        }
      },
    });
  }

  // ---------- Campaign Edit Modal ----------

  createFormGroup(): void {
    this.formGroup = this.formBuilder.group({
      id: [''],
      name: ['', [Validators.required, Validators.maxLength(255)]],
      userId: [this.userId],
      status: ['', [Validators.required]],
      specialAdCategories: ['', [Validators.maxLength(255)]],
      objective: ['', [Validators.required, Validators.maxLength(100)]],
      adAccountId: [
        this.actId ?? '',
        [Validators.required, Validators.maxLength(255)],
      ],
      externalId: ['', [Validators.maxLength(255)]],
      platform: ['', [Validators.required]],
    });
  }

  updateFormGroup(campaign: Campaign): void {
    this.formGroup = this.formBuilder.group({
      id: [campaign.id],
      name: [campaign.name, [Validators.required, Validators.maxLength(255)]],
      userId: [this.userId],
      status: [campaign.status, [Validators.required]],
      specialAdCategories: [
        campaign.specialAdCategories ?? '',
        [Validators.maxLength(255)],
      ],
      objective: [
        campaign.objective,
        [Validators.required, Validators.maxLength(100)],
      ],
      adAccountId: [
        (campaign.adAccountId || this.actId) ?? '',
        [Validators.required, Validators.maxLength(255)],
      ],
      externalId: [campaign.externalId ?? '', [Validators.maxLength(255)]],
      platform: [campaign.platform, [Validators.required]],
    });
  }

  hasError(controlName: string, errorType: string): boolean {
    const control = this.formGroup.get(controlName);
    return !!(control && control.touched && control.hasError(errorType));
  }

  openModal(guid: any): void {
    this.activeModalTab = 'form';
    if (guid != null) {
      const campaign = this.campaigns.find(
        (c) => String(c.id) === String(guid),
      );
      if (campaign) {
        this.isEdit = true;
        this.updateFormGroup(campaign);
        this.title = `Edit Campaign - ${campaign.name}`;
      } else {
        this.title = 'Campaign Not Found';
        this.isEdit = false;
        this.createFormGroup();
      }
    } else {
      this.title = 'Create Campaign';
      this.isEdit = false;
      this.createFormGroup();
    }
    this.isModalOpen = true;
  }

  onSubmit(): void {
    if (!this.formGroup.valid) {
      if (!this.authStore.isSessionExpiredRedirect()) {
        this.toastr.error('Please fill all required fields');
      }
      return;
    }

    const payload = {
      ...this.formGroup.value,
      userId: this.userId,
    };

    const action = this.isEdit
      ? this.campaignService.updateById(
          this.formGroup.controls['id'].value,
          payload,
        )
      : this.campaignService.create(payload);

    action.subscribe({
      next: () => {
        this.toastr.success(
          `Campaign ${this.isEdit ? 'updated' : 'created'} successfully!`,
        );
        this.formGroup.reset();
        this.isModalOpen = false;
        this.fetchCampaigns();
      },
      error: (err: any) => {
        if (!this.authStore.isSessionExpiredRedirect()) {
          this.toastr.error(
            CoreService.extractErrorMessage(err, `Failed to ${this.isEdit ? 'update' : 'create'} campaign`),
          );
        }
      },
    });
  }

  onCancel(): void {
    if (this.formGroup) this.formGroup.reset();
    this.isModalOpen = false;
    this.isEdit = false;
  }

  // ---------- Row Click ----------

  onRowClick(event: { item: any; originalEvent?: MouseEvent }): void {
    if (this.activeTabIndex !== 0) return;
    const campaign = this.campaigns.find(
      (c) => String(c.id) === String(event.item.guid),
    );
    if (campaign) {
      this.selectedCampaign = campaign;
      setTimeout(() => {
        this.topItemData = campaign;
        this.topItemType = 'campaign';
        this.cdr.detectChanges();
      }, 0);
    }
  }

  onTopItemChange(topItem: any): void {
    setTimeout(() => {
      if (topItem && this.activeTabIndex === 0) {
        const campaign = this.campaigns.find(
          (c) => String(c.id) === String(topItem.guid),
        );
        if (campaign) {
          this.topItemData = campaign;
          this.topItemType = 'campaign';
        }
      } else {
        this.topItemData = null;
        this.topItemType = null;
      }
      this.cdr.detectChanges();
    }, 0);
  }

  // ---------- Analytics ----------

  initializeAnalytics(): void {
    this.analyticsCards = [
      {
        title: 'Campaigns',
        total: 0,
        active: 0,
        paused: 0,
        deleted: 0,
        activePercentage: 0,
        pausedPercentage: 0,
        deletedPercentage: 0,
      },
      {
        title: 'Ad Sets',
        total: 0,
        active: 0,
        paused: 0,
        deleted: 0,
        activePercentage: 0,
        pausedPercentage: 0,
        deletedPercentage: 0,
      },
      {
        title: 'Ads',
        total: 0,
        active: 0,
        paused: 0,
        deleted: 0,
        activePercentage: 0,
        pausedPercentage: 0,
        deletedPercentage: 0,
      },
    ];
  }

  private preloadAnalyticsWithMostRecent(): void {
    if (this.campaigns.length > 0) {
      const sorted = [...this.campaigns].sort((a, b) => {
        const dateA = new Date(a.updatedAt || 0).getTime();
        const dateB = new Date(b.updatedAt || 0).getTime();
        return dateB - dateA;
      });
      this.preloadedItemData = sorted[0];
    }
  }

  // ---------- Column Customiser ----------

  /** Returns a fresh deep-cloned column config for the given tab, expanding
   *  isConversionEvent markers into _total / _value / _cost triples. */
  getColumnConfigForTab(tabIndex: number): ColumnDef[] {
    const source =
      tabIndex === 0
        ? CAMPAIGN_COLUMNS
        : tabIndex === 1
          ? ADSET_COLUMNS
          : AD_COLUMNS;
    const base: ColumnDef[] = deepClone(source as ColumnDef[]);
    const expanded: ColumnDef[] = [];
    let convIdx = 0;
    for (const col of base) {
      if (col.isConversionEvent) {
        const id = col.key.replace(/^conv_/, '');
        const baseOrder = 2000 + convIdx * 3;
        expanded.push(
          {
            ...col,
            key: `conv_${id}_total`,
            label: `${col.label} (total)`,
            order: baseOrder,
            isConversionEvent: false,
          },
          {
            ...col,
            key: `conv_${id}_value`,
            label: `${col.label} (value)`,
            order: baseOrder + 1,
            isConversionEvent: false,
          },
          {
            ...col,
            key: `conv_${id}_cost`,
            label: `${col.label} (cost)`,
            order: baseOrder + 2,
            isConversionEvent: false,
          },
        );
        convIdx++;
      } else {
        expanded.push(col);
      }
    }
    return expanded;
  }

  /** Returns the currently active tab's mutable column config. */
  private getCurrentTabConfig(): ColumnDef[] {
    switch (this.activeTabIndex) {
      case 1:
        return this.adSetColumnConfig;
      case 2:
        return this.adColumnConfig;
      default:
        return this.campaignColumnConfig;
    }
  }

  private setColumnConfigForTab(config: ColumnDef[]): void {
    switch (this.activeTabIndex) {
      case 1:
        this.adSetColumnConfig = config;
        break;
      case 2:
        this.adColumnConfig = config;
        break;
      default:
        this.campaignColumnConfig = config;
    }
  }

  openColumnCustomiser(): void {
    this.tempColumnConfig = this.getCurrentTabConfig().map((c) => ({ ...c }));
    this.columnSearch = '';
    this.customiserCategoryIndex = 0;
    this.isColumnCustomiserOpen = true;
  }

  closeColumnCustomiser(): void {
    this.isColumnCustomiserOpen = false;
  }

  applyColumnConfig(): void {
    this.setColumnConfigForTab(this.tempColumnConfig.map((c) => ({ ...c })));
    switch (this.activeTabIndex) {
      case 1:
        this.setAdSetHeaders();
        break;
      case 2:
        this.setAdHeaders();
        break;
      default:
        this.setCampaignHeaders();
    }
    this.isColumnCustomiserOpen = false;
    this.cdr.detectChanges();

    const entityType = this.tabIndexToEntityType(this.activeTabIndex);
    const selectedKeys = this.tempColumnConfig
      .filter((c) => c.enabled && !c.alwaysVisible)
      .map((c) => c.key);
    this.userConfigService.saveColumnConfig(entityType, selectedKeys).subscribe({
      next: () => {},
      error: () => {
        this.toastr.warning(
          'Column preferences could not be saved — they will reset on next login.',
        );
      },
    });
  }

  getAvailableColumns(): ColumnDef[] {
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    // Custom tab has no toggle-able columns
    if (!cat || cat.key === 'custom') return [];
    const categoryKey = cat.key;
    const q = this.columnSearch.toLowerCase().trim();
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        c.category === categoryKey &&
        (!q || c.label.toLowerCase().includes(q)),
    );
  }

  getSelectedColumns(): ColumnDef[] {
    return this.tempColumnConfig.filter((c) => !c.alwaysVisible && c.enabled);
  }

  toggleTempColumn(col: ColumnDef): void {
    const found = this.tempColumnConfig.find((c) => c.key === col.key);
    if (found) found.enabled = !found.enabled;
  }

  removeTempColumn(col: ColumnDef): void {
    const found = this.tempColumnConfig.find((c) => c.key === col.key);
    if (found) found.enabled = false;
  }

  /** Safe cell value lookup — returns '—' for any missing or unknown key */
  getCellValue(item: any, key: string): string {
    try {
      return item?.rows?.find((r: any) => r.key === key)?.value ?? '—';
    } catch {
      return '—';
    }
  }

  /** Returns true if the given key is enabled in the temp column config */
  isTempColEnabled(key: string): boolean {
    return this.tempColumnConfig.find((c) => c.key === key)?.enabled ?? false;
  }

  /** Toggles a temp column by key (used for conversion table checkboxes) */
  toggleTempColumnByKey(key: string): void {
    const col = this.tempColumnConfig.find((c) => c.key === key);
    if (col) col.enabled = !col.enabled;
  }

  toggleSection(section: string): void {
    if (this.collapsedSections.has(section))
      this.collapsedSections.delete(section);
    else this.collapsedSections.add(section);
  }

  isSectionCollapsed(section: string): boolean {
    return this.collapsedSections.has(section);
  }

  getSectionSelectedCount(keys: string[]): number {
    return this.tempColumnConfig.filter(
      (c) => keys.includes(c.key) && c.enabled,
    ).length;
  }

  getAvailableColumnsByKeys(keys: string[]): ColumnDef[] {
    const q = this.columnSearch.toLowerCase().trim();
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        keys.includes(c.key) &&
        (!q || c.label.toLowerCase().includes(q)),
    );
  }

  /** Returns ordered distinct parentSection/section groups for the current non-KM tab */
  getTabSections(): { parentSection: string; section: string; id: string }[] {
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    if (!cat || cat.key === 'custom' || cat.key === 'key_metrics') return [];
    const q = this.columnSearch.toLowerCase().trim();
    const seen = new Set<string>();
    const result: { parentSection: string; section: string; id: string }[] = [];
    for (const col of this.tempColumnConfig) {
      if (col.alwaysVisible || col.category !== cat.key) continue;
      if (q && !col.label.toLowerCase().includes(q)) continue;
      const ps = col.parentSection ?? '';
      const id = `${ps}|||${col.section}`;
      if (!seen.has(id)) {
        seen.add(id);
        result.push({ parentSection: ps, section: col.section, id });
      }
    }
    return result;
  }

  /** Returns columns for a specific parentSection+section in the current tab */
  getColumnsBySection(parentSection: string, section: string): ColumnDef[] {
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    if (!cat) return [];
    const q = this.columnSearch.toLowerCase().trim();
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        c.category === cat.key &&
        (c.parentSection ?? '') === parentSection &&
        c.section === section &&
        (!q || c.label.toLowerCase().includes(q)),
    );
  }

  /** Count selected columns in a given tab section (by id = 'parentSection|||section') */
  getTabSectionSelectedCount(sectionId: string): number {
    const [parentSection, section] = sectionId.split('|||');
    const cat = this.customiserCategories[this.customiserCategoryIndex];
    if (!cat) return 0;
    return this.tempColumnConfig.filter(
      (c) =>
        !c.alwaysVisible &&
        c.category === cat.key &&
        (c.parentSection ?? '') === parentSection &&
        c.section === section &&
        c.enabled,
    ).length;
  }

  generatePerformanceData(): void {
    const data: PerformanceData[] = [];
    const baseDate = new Date();
    baseDate.setDate(baseDate.getDate() - this.selectedTimePeriod);

    for (let i = 0; i < this.selectedTimePeriod; i++) {
      const date = new Date(baseDate);
      date.setDate(date.getDate() + i);
      const dayOfWeek = date.getDay();
      const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
      const weekendMultiplier = isWeekend ? 0.65 : 1.0;
      const trendMultiplier =
        1 + Math.sin((i / this.selectedTimePeriod) * Math.PI * 2) * 0.2;
      const impressions = Math.floor(
        (12000 + Math.random() * 8000) * weekendMultiplier * trendMultiplier,
      );
      const reach = Math.floor(impressions * (0.72 + Math.random() * 0.18));
      const people = Math.floor(reach * (0.82 + Math.random() * 0.13));
      const clicks = Math.floor(impressions * (0.018 + Math.random() * 0.027));
      const conversions = Math.floor(clicks * (0.025 + Math.random() * 0.08));
      const cost = parseFloat(
        (clicks * (1.1 + Math.random() * 1.3)).toFixed(2),
      );

      data.push({
        date: date.toISOString().split('T')[0],
        impressions,
        reach,
        people,
        clicks,
        conversions,
        cost,
      });
    }
    this.performanceData = data;
  }
}
