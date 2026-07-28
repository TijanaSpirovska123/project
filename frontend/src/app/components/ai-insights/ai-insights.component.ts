import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
} from '@angular/core';
import { finalize, forkJoin } from 'rxjs';
import { AppToastrService } from '../../services/core/app-toastr.service';
import { AuthStoreService } from '../../services/core/auth-store.service';
import { CoreService } from '../../services/core/core.service';
import { CampaignService } from '../../services/campaign/campaign.service';
import { AdSetService } from '../../services/adset/adset.service';
import { AdService } from '../../services/ad/ad.service';
import { AiInsightsService } from '../../services/insights/ai-insights.service';
import { Provider } from '../../data/provider/provider.enum';
import { AD_PLATFORM_OPTIONS } from '../../data/provider/provider-options';
import { Campaign } from '../../models/campaign/campaign';
import { AdSetResponse, AdResponse } from '../../models/adset/adset.model';
import { DropdownOption } from '../shared/searchable-dropdown.component';
import { DatePresetId, DateRangeSelection } from '../insights/adflow-date-range-picker.component';
import { DEFAULT_DATE_SELECTION, ROLLING_PRESET_DAYS } from '../../data/insights/insights-fields';
import {
  AI_METRIC_OPTIONS,
  AI_SUGGESTED_QUESTIONS,
  DEFAULT_AI_METRICS,
} from '../../data/insights/ai-insights-fields';
import {
  AiChatMessage,
  AiInsightObjectType,
  AiInsightResponse,
  AnalysisContextRequest,
  CanonicalMetric,
  GenerateAiInsightRequest,
} from '../../models/insights/ai-insight.model';

let messageSeq = 0;

@Component({
  selector: 'app-ai-insights',
  standalone: false,
  templateUrl: './ai-insights.component.html',
  styleUrls: ['./ai-insights.component.scss'],
})
export class AiInsightsComponent implements OnInit {
  @ViewChild('chatBody') chatBodyRef?: ElementRef<HTMLDivElement>;

  userId = '';
  isInitialLoading = false;
  isAnalyzing = false;

  platform: Provider = Provider.META;
  readonly platformOptionsList = AD_PLATFORM_OPTIONS;

  campaigns: Campaign[] = [];
  allAdSets: AdSetResponse[] = [];
  allAds: AdResponse[] = [];

  adSets: AdSetResponse[] = [];
  ads: AdResponse[] = [];

  selectedCampaign: Campaign | null = null;
  selectedAdSet: AdSetResponse | null = null;
  selectedAd: AdResponse | null = null;

  dateSelection: DateRangeSelection = { ...DEFAULT_DATE_SELECTION };
  dateStart = '';
  dateStop = '';

  includeTrend = true;
  readonly metricOptions = AI_METRIC_OPTIONS;
  readonly suggestedQuestions = AI_SUGGESTED_QUESTIONS;
  selectedMetrics: Set<CanonicalMetric> = new Set(DEFAULT_AI_METRICS);

  questionInput = '';
  messages: AiChatMessage[] = [];

  constructor(
    private readonly toastr: AppToastrService,
    private readonly authStore: AuthStoreService,
    private readonly campaignService: CampaignService,
    private readonly adSetService: AdSetService,
    private readonly adService: AdService,
    private readonly aiInsightsService: AiInsightsService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.userId = this.authStore.getUserId();
    this.applyDateSelection(DEFAULT_DATE_SELECTION);
    this.loadCampaignData();
  }

  private loadCampaignData(): void {
    this.isInitialLoading = true;
    forkJoin({
      campaigns: this.campaignService.getAllByPlatform(this.platform),
      adSets: this.adSetService.getAllByPlatform(this.platform),
      ads: this.adService.getAllByPlatform(this.platform),
    })
      .pipe(
        finalize(() => {
          this.isInitialLoading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: ({ campaigns, adSets, ads }: any) => {
          this.campaigns = campaigns?.data ?? [];
          this.allAdSets = adSets?.data ?? [];
          this.allAds = ads?.data ?? [];
        },
        error: (err: any) => this.handleError(err, 'Failed to load campaign data'),
      });
  }

  // ---------- Selection ----------

  get platformOptions(): DropdownOption[] {
    return this.platformOptionsList.map((p) => ({ value: p.value, label: p.label }));
  }

  onPlatformChange(opt: DropdownOption | null): void {
    this.platform = (opt?.value as Provider) ?? Provider.META;
    this.selectedCampaign = null;
    this.selectedAdSet = null;
    this.selectedAd = null;
    this.campaigns = [];
    this.allAdSets = [];
    this.allAds = [];
    this.adSets = [];
    this.ads = [];
    this.loadCampaignData();
  }

  get campaignOptions(): DropdownOption[] {
    return this.campaigns
      .filter((c) => !!c.externalId)
      .map((c) => ({ value: c.externalId as string, label: c.name }));
  }

  get adSetOptions(): DropdownOption[] {
    return this.adSets
      .filter((a) => !!a.externalId)
      .map((a) => ({ value: a.externalId, label: a.name }));
  }

  get adOptions(): DropdownOption[] {
    return this.ads
      .filter((a) => !!a.externalId)
      .map((a) => ({ value: a.externalId, label: a.name }));
  }

  onCampaignChange(opt: DropdownOption | null): void {
    this.selectedCampaign = opt
      ? (this.campaigns.find((c) => c.externalId === opt.value) ?? null)
      : null;
    this.selectedAdSet = null;
    this.selectedAd = null;
    this.ads = [];
    this.adSets = this.selectedCampaign
      ? this.allAdSets.filter((a) => a.campaignId === this.selectedCampaign!.id)
      : [];
  }

  onAdSetChange(opt: DropdownOption | null): void {
    this.selectedAdSet = opt
      ? (this.adSets.find((a) => a.externalId === opt.value) ?? null)
      : null;
    this.selectedAd = null;
    this.ads = this.selectedAdSet
      ? this.allAds.filter((a) => a.adSetId === this.selectedAdSet!.id)
      : [];
  }

  onAdChange(opt: DropdownOption | null): void {
    this.selectedAd = opt ? (this.ads.find((a) => a.externalId === opt.value) ?? null) : null;
  }

  get selectionAccountId(): string | null {
    return this.selectedAd?.adAccountId ?? this.selectedAdSet?.adAccountId ?? this.selectedCampaign?.adAccountId ?? null;
  }

  get canSend(): boolean {
    return !!this.selectedCampaign && !!this.selectionAccountId && this.dateRangeSelected && !this.isAnalyzing;
  }

  get dateRangeSelected(): boolean {
    return !!this.dateStart && !!this.dateStop;
  }

  // ---------- Date range ----------

  onDateSelectionChange(selection: DateRangeSelection): void {
    this.applyDateSelection(selection);
  }

  private applyDateSelection(selection: DateRangeSelection): void {
    this.dateSelection = selection;
    const resolved = selection.preset ? this.resolvePresetDates(selection.preset) : {
      dateStart: selection.dateStart ?? '',
      dateStop: selection.dateStop ?? '',
    };
    this.dateStart = resolved.dateStart;
    this.dateStop = resolved.dateStop;
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

    return { dateStart: this.formatDate(start), dateStop: this.formatDate(stop) };
  }

  private formatDate(d: Date): string {
    const month = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${d.getFullYear()}-${month}-${day}`;
  }

  // ---------- Metrics ----------

  isMetricSelected(key: CanonicalMetric): boolean {
    return this.selectedMetrics.has(key);
  }

  toggleMetric(key: CanonicalMetric): void {
    const next = new Set(this.selectedMetrics);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.selectedMetrics = next;
  }

  // ---------- Context label ----------

  get objectType(): AiInsightObjectType {
    if (this.selectedAd) return 'AD';
    if (this.selectedAdSet) return 'ADSET';
    return 'CAMPAIGN';
  }

  get contextLabel(): string {
    const parts: string[] = [];
    if (this.selectedCampaign) parts.push(this.selectedCampaign.name);
    if (this.selectedAdSet) parts.push(this.selectedAdSet.name);
    if (this.selectedAd) parts.push(this.selectedAd.name);
    const scope = parts.length ? parts.join(' › ') : 'No campaign selected';
    const range = this.dateStart && this.dateStop ? `${this.dateStart} → ${this.dateStop}` : '';
    return range ? `${scope} · ${range}` : scope;
  }

  // ---------- Chat ----------

  useSuggestion(text: string): void {
    this.questionInput = text;
  }

  onComposerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  clearConversation(): void {
    this.messages = [];
  }

  send(): void {
    if (!this.canSend) {
      if (!this.selectedCampaign) {
        this.toastr.warning('Select a campaign first');
      } else if (!this.dateRangeSelected) {
        this.toastr.warning('Select a date range first');
      }
      return;
    }

    const question = this.questionInput.trim();
    if (question && question.length < 3) {
      this.toastr.warning('Question must be at least 3 characters, or left empty for a general analysis');
      return;
    }

    const contextRequest = this.buildContextRequest();
    const contextLabel = this.contextLabel;

    const userMessage: AiChatMessage = {
      id: this.nextMessageId(),
      role: 'user',
      question: question || 'Analyze current selection',
      createdAt: Date.now(),
      contextLabel,
    };
    this.messages = [...this.messages, userMessage];
    this.questionInput = '';
    this.isAnalyzing = true;
    this.scrollToBottomSoon();

    const body: GenerateAiInsightRequest = {
      contextRequest,
      question: question || null,
    };

    this.aiInsightsService
      .analyze(body)
      .pipe(
        finalize(() => {
          this.isAnalyzing = false;
          this.cdr.markForCheck();
          this.scrollToBottomSoon();
        }),
      )
      .subscribe({
        next: (res) => {
          const response: AiInsightResponse | undefined = (res as any)?.data ?? (res as any);
          this.messages = [
            ...this.messages,
            {
              id: this.nextMessageId(),
              role: 'assistant',
              response,
              createdAt: Date.now(),
              contextLabel,
            },
          ];
        },
        error: (err: any) => {
          const message = this.extractAiErrorMessage(err);
          this.messages = [
            ...this.messages,
            {
              id: this.nextMessageId(),
              role: 'error',
              errorMessage: message,
              createdAt: Date.now(),
              contextLabel,
            },
          ];
          if (!this.authStore.isSessionExpiredRedirect()) {
            this.toastr.error(message);
          }
        },
      });
  }

  private buildContextRequest(): AnalysisContextRequest {
    const accountId = this.selectionAccountId as string;
    const request: AnalysisContextRequest = {
      filter: {
        provider: this.platform,
        adAccountId: this.formatAccountId(accountId),
        objectType: this.objectType,
        dateStart: this.dateStart,
        dateStop: this.dateStop,
        ...(this.objectType === 'AD'
          ? { adIds: [this.selectedAd!.externalId] }
          : this.objectType === 'ADSET'
            ? { adSetIds: [this.selectedAdSet!.externalId] }
            : { campaignIds: [this.selectedCampaign!.externalId as string] }),
      },
      comparison: {
        enabled: !!this.dateSelection.compareToPrevious,
        mode: 'PREVIOUS_PERIOD',
      },
      timeSeries: {
        enabled: this.includeTrend,
        granularity: 'DAY',
        metrics: Array.from(this.selectedMetrics),
      },
      includeFindings: true,
      includeDataQuality: true,
    };
    return request;
  }

  private formatAccountId(accountId: string): string {
    return accountId.startsWith('act_') ? accountId : `act_${accountId}`;
  }

  private nextMessageId(): string {
    messageSeq += 1;
    return `ai-msg-${messageSeq}`;
  }

  private scrollToBottomSoon(): void {
    setTimeout(() => {
      const el = this.chatBodyRef?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 0);
  }

  private extractAiErrorMessage(err: any): string {
    const aiMessage = err?.error?.message;
    if (typeof aiMessage === 'string' && aiMessage.trim()) return aiMessage;
    return CoreService.extractErrorMessage(err, 'Failed to generate AI insights');
  }

  private handleError(err: any, fallback: string): void {
    if (!this.authStore.isSessionExpiredRedirect()) {
      this.toastr.error(err ? CoreService.extractErrorMessage(err, fallback) : fallback);
    }
  }

  // ---------- Display helpers ----------

  formatConfidence(score: number | undefined | null): string {
    if (score === undefined || score === null || Number.isNaN(score)) return '—';
    return `${Math.round(score * 100)}%`;
  }

  severityClass(severity: string): string {
    return `severity-${severity.toLowerCase()}`;
  }

  priorityClass(priority: string): string {
    return `priority-${priority.toLowerCase()}`;
  }

  trackByMessageId(_: number, message: AiChatMessage): string {
    return message.id;
  }
}
