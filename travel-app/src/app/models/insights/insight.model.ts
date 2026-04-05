export interface InsightMetric {
  name: string;
  valueNumber: number | null;
  valueText: string | null;
}

export interface InsightSnapshot {
  id: number;
  provider: string;
  adAccountId: string;
  objectType: 'CAMPAIGN' | 'ADSET' | 'AD';
  objectExternalId: string;
  dateStart: string;
  dateStop: string;
  timeIncrement: number;
  createdAt: string;
  updatedAt: string;
  metrics: InsightMetric[];
  rawData: any;
}

export interface InsightResponse {
  data: InsightSnapshot[];
  success: boolean;
  error: string;
}

export interface InsightSyncRequest {
  provider: string;
  adAccountId?: string;
  objectExternalIds?: string[];
  datePreset?: string;
  dateStart?: string;
  dateStop?: string;
  objectType?: string;
  fetchMode?: string;
  timeIncrementAllDays?: boolean;
  limit?: number;
  actionBreakdowns?: string;
  breakdowns?: any;
  actionReportTime?: string;
}

export interface MetricBlock {
  index: number;
  metricKey: string | null;
  label: string;
  value: string;
  icon: string;
  trend?: number;
  trendDirection?: 'up' | 'down' | 'neutral';
}

export const ALL_INSIGHT_METRICS: string[] = [
  'account_currency',
  'actions',
  'actions.landing_page_view',
  'actions.link_click',
  'actions.omni_landing_page_view',
  'actions.onsite_conversion.post_net_like',
  'actions.page_engagement',
  'actions.post_engagement',
  'actions.post_interaction_gross',
  'actions.post_reaction',
  'ad_id',
  'clicks',
  'cost_per_unique_action_type',
  'cost_per_unique_action_type.link_click',
  'cpc',
  'cpm',
  'cpp',
  'ctr',
  'date_start',
  'date_stop',
  'impressions',
  'outbound_clicks',
  'outbound_clicks.outbound_click',
  'reach',
  'spend',
];

export const METRIC_ICONS: Record<string, string> = {
  account_currency: 'account_balance_wallet',
  actions: 'touch_app',
  'actions.landing_page_view': 'web',
  'actions.link_click': 'link',
  'actions.omni_landing_page_view': 'travel_explore',
  'actions.onsite_conversion.post_net_like': 'thumb_up',
  'actions.page_engagement': 'pages',
  'actions.post_engagement': 'engagement',
  'actions.post_interaction_gross': 'interactions',
  'actions.post_reaction': 'add_reaction',
  ad_id: 'badge',
  clicks: 'ads_click',
  cost_per_unique_action_type: 'price_check',
  'cost_per_unique_action_type.link_click': 'price_change',
  cpc: 'payment',
  cpm: 'bar_chart',
  cpp: 'analytics',
  ctr: 'percent',
  date_start: 'calendar_today',
  date_stop: 'event',
  impressions: 'visibility',
  outbound_clicks: 'open_in_new',
  'outbound_clicks.outbound_click': 'launch',
  reach: 'people',
  spend: 'attach_money',
};

export const DEFAULT_METRICS: string[] = [
  'impressions',
  'reach',
  'clicks',
  'spend',
  'ctr',
  'cpm',
];
