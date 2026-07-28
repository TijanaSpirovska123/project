// Mirrors the backend contract exposed by AiInsightsController (POST /api/insights/ai/analyze),
// which forwards to AnalysisContextRequestDto / AnalysisContextBuilder (Phase-2 analytics) and
// then to the Python FastAPI assistant. Field names match the Java DTOs' Jackson camelCase output.

export type CanonicalMetric =
  | 'SPEND'
  | 'IMPRESSIONS'
  | 'REACH'
  | 'CLICKS'
  | 'UNIQUE_CLICKS'
  | 'OUTBOUND_CLICKS'
  | 'LANDING_PAGE_VIEWS'
  | 'LEADS'
  | 'CONVERSIONS'
  | 'PURCHASES'
  | 'PURCHASE_VALUE'
  | 'CTR'
  | 'CPC'
  | 'CPM'
  | 'FREQUENCY'
  | 'CONVERSION_RATE'
  | 'COST_PER_LEAD'
  | 'COST_PER_CONVERSION'
  | 'COST_PER_PURCHASE'
  | 'ROAS';

export type AiInsightObjectType = 'ACCOUNT' | 'CAMPAIGN' | 'ADSET' | 'AD';

export type AiTimeGranularity = 'DAY' | 'WEEK' | 'MONTH';

export type AiComparisonMode = 'PREVIOUS_PERIOD' | 'PREVIOUS_YEAR' | 'CUSTOM';

export interface AiInsightPeriod {
  start: string;
  stop: string;
}

/** Shared filter shape — only one of campaignIds/adSetIds/adIds may be populated per request. */
export interface AnalyticsFilterRequest {
  provider: string;
  adAccountId: string;
  objectType?: AiInsightObjectType;
  campaignIds?: string[];
  adSetIds?: string[];
  adIds?: string[];
  dateStart: string;
  dateStop: string;
}

export interface ComparisonRequest {
  enabled: boolean;
  mode?: AiComparisonMode;
  customPeriod?: AiInsightPeriod;
}

export interface TimeSeriesRequest {
  enabled: boolean;
  granularity?: AiTimeGranularity;
  metrics?: CanonicalMetric[];
  includeInactivePeriods?: boolean;
}

export interface AnalysisContextRequest {
  filter: AnalyticsFilterRequest;
  comparison?: ComparisonRequest;
  timeSeries?: TimeSeriesRequest;
  includeFindings?: boolean;
  includeDataQuality?: boolean;
}

export interface GenerateAiInsightRequest {
  contextRequest: AnalysisContextRequest;
  question?: string | null;
}

export type AiInsightSeverity = 'Positive' | 'Information' | 'Warning' | 'Critical';

export interface AiInsightItem {
  title: string;
  explanation: string;
  evidence: string[];
  severity: AiInsightSeverity;
}

export type AiRecommendationPriority = 'Low' | 'Medium' | 'High';

export interface AiRecommendationItem {
  action: string;
  reason: string;
  priority: AiRecommendationPriority;
  suggestedChange?: string | null;
  risk?: string | null;
}

export interface AiInsightResponse {
  summary: string;
  answer: string;
  keyInsights: AiInsightItem[];
  risks: AiInsightItem[];
  recommendations: AiRecommendationItem[];
  limitations: string[];
  conclusion: string;
  confidenceScore: number;
}

/** One turn of the on-screen conversation. Each turn is answered statelessly by the backend —
 *  no prior chat history is sent — so `contextLabel` lets the user see what scope/date-range a
 *  given answer was generated against even after they change the selection above. */
export interface AiChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'error';
  question?: string;
  response?: AiInsightResponse;
  errorMessage?: string;
  createdAt: number;
  contextLabel: string;
}
