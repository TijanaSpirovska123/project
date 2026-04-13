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

export interface MetricConfig {
  key: string;
  label: string;
  format: 'number' | 'currency' | 'percent' | 'decimal2';
  category: string;
}

// ─── Metric Configuration ────────────────────────────────────────────────────
// Keys are raw Meta API field names (flat) or dot-notation expanded keys
// (e.g. "actions.link_click" = entry['actions'] array item where action_type='link_click')

export const METRIC_CONFIG: MetricConfig[] = [
  // ── Core ──
  { key: 'impressions',           label: 'Impressions',          format: 'number',   category: 'Core' },
  { key: 'reach',                 label: 'Reach',                format: 'number',   category: 'Core' },
  { key: 'clicks',                label: 'Clicks',               format: 'number',   category: 'Core' },
  { key: 'spend',                 label: 'Spend',                format: 'currency', category: 'Core' },
  { key: 'ctr',                   label: 'CTR',                  format: 'percent',  category: 'Core' },
  { key: 'cpm',                   label: 'CPM',                  format: 'currency', category: 'Core' },
  { key: 'cpc',                   label: 'CPC',                  format: 'currency', category: 'Core' },
  { key: 'frequency',             label: 'Frequency',            format: 'decimal2', category: 'Core' },
  { key: 'unique_clicks',         label: 'Unique Clicks',        format: 'number',   category: 'Core' },
  { key: 'unique_ctr',            label: 'Unique CTR',           format: 'percent',  category: 'Core' },
  { key: 'cpp',                   label: 'CPP',                  format: 'currency', category: 'Core' },
  { key: 'cost_per_unique_click', label: 'Cost / Unique Click',  format: 'currency', category: 'Core' },
  { key: 'social_spend',          label: 'Social Spend',         format: 'currency', category: 'Core' },

  // ── Engagement ──
  { key: 'inline_link_clicks',                    label: 'Link Clicks',         format: 'number',  category: 'Engagement' },
  { key: 'inline_link_click_ctr',                 label: 'Link CTR',            format: 'percent', category: 'Engagement' },
  { key: 'inline_post_engagement',                label: 'Post Engagement',     format: 'number',  category: 'Engagement' },
  { key: 'outbound_clicks.outbound_click',        label: 'Outbound Clicks',     format: 'number',  category: 'Engagement' },
  { key: 'unique_outbound_clicks.outbound_click', label: 'Unique Outbound',     format: 'number',  category: 'Engagement' },
  { key: 'website_ctr.link_click',                label: 'Website CTR',         format: 'percent', category: 'Engagement' },
  { key: 'actions.post_engagement',               label: 'Post Engagements',    format: 'number',  category: 'Engagement' },
  { key: 'actions.link_click',                    label: 'Link Click Actions',  format: 'number',  category: 'Engagement' },

  // ── Video ──
  { key: 'video_play_actions.video_view',                     label: 'Video Plays',         format: 'number',   category: 'Video' },
  { key: 'video_thruplay_watched_actions.video_view',         label: 'ThruPlays',           format: 'number',   category: 'Video' },
  { key: 'video_continuous_2_sec_watched_actions.video_view', label: '2s Video Views',      format: 'number',   category: 'Video' },
  { key: 'video_30_sec_watched_actions.video_view',           label: '30s Video Views',     format: 'number',   category: 'Video' },
  { key: 'video_p25_watched_actions.video_view',              label: 'Video 25% Watched',   format: 'number',   category: 'Video' },
  { key: 'video_p50_watched_actions.video_view',              label: 'Video 50% Watched',   format: 'number',   category: 'Video' },
  { key: 'video_p75_watched_actions.video_view',              label: 'Video 75% Watched',   format: 'number',   category: 'Video' },
  { key: 'video_p100_watched_actions.video_view',             label: 'Video Completed',     format: 'number',   category: 'Video' },
  { key: 'video_avg_time_watched_actions.video_view',         label: 'Avg Watch Time (s)',  format: 'decimal2', category: 'Video' },

  // ── Conversions ──
  { key: 'conversions',                                       label: 'Conversions',         format: 'number',   category: 'Conversions' },
  { key: 'conversion_values',                                 label: 'Conversion Value',    format: 'currency', category: 'Conversions' },
  { key: 'actions.offsite_conversion.fb_pixel_purchase',      label: 'Purchases',           format: 'number',   category: 'Conversions' },
  { key: 'purchase_roas.omni_purchase',                       label: 'Purchase ROAS',       format: 'decimal2', category: 'Conversions' },
  { key: 'actions.offsite_conversion.fb_pixel_lead',          label: 'Leads',               format: 'number',   category: 'Conversions' },
  { key: 'actions.offsite_conversion.fb_pixel_add_to_cart',   label: 'Add to Carts',        format: 'number',   category: 'Conversions' },
  { key: 'actions.app_install',                               label: 'App Installs',        format: 'number',   category: 'Conversions' },
  { key: 'cost_per_conversion',                               label: 'Cost / Conversion',   format: 'currency', category: 'Conversions' },

  // ── Estimated ──
  { key: 'estimated_ad_recall_rate', label: 'Est. Recall Rate', format: 'percent', category: 'Estimated' },
  { key: 'estimated_ad_recallers',   label: 'Est. Recallers',   format: 'number',  category: 'Estimated' },
];

export const ALL_INSIGHT_METRICS: string[] = METRIC_CONFIG.map(m => m.key);

export const METRIC_ICONS: Record<string, string> = {
  // Core
  impressions:           'visibility',
  reach:                 'people',
  clicks:                'ads_click',
  spend:                 'attach_money',
  ctr:                   'percent',
  cpm:                   'bar_chart',
  cpc:                   'payment',
  frequency:             'repeat',
  unique_clicks:         'person',
  unique_ctr:            'person_search',
  cpp:                   'analytics',
  cost_per_unique_click: 'price_check',
  social_spend:          'groups',
  // Engagement
  inline_link_clicks:                    'link',
  inline_link_click_ctr:                 'link',
  inline_post_engagement:                'thumb_up',
  'outbound_clicks.outbound_click':      'open_in_new',
  'unique_outbound_clicks.outbound_click': 'launch',
  'website_ctr.link_click':              'travel_explore',
  'actions.post_engagement':             'engagement',
  'actions.link_click':                  'ads_click',
  // Video
  'video_play_actions.video_view':                     'play_circle',
  'video_thruplay_watched_actions.video_view':         'check_circle',
  'video_continuous_2_sec_watched_actions.video_view': 'looks_two',
  'video_30_sec_watched_actions.video_view':           'timer',
  'video_p25_watched_actions.video_view':              'looks_one',
  'video_p50_watched_actions.video_view':              'looks_two',
  'video_p75_watched_actions.video_view':              'looks_3',
  'video_p100_watched_actions.video_view':             'looks_4',
  'video_avg_time_watched_actions.video_view':         'schedule',
  // Conversions
  conversions:                                       'shopping_cart',
  conversion_values:                                 'attach_money',
  'actions.offsite_conversion.fb_pixel_purchase':    'shopping_bag',
  'purchase_roas.omni_purchase':                     'trending_up',
  'actions.offsite_conversion.fb_pixel_lead':        'person_add',
  'actions.offsite_conversion.fb_pixel_add_to_cart': 'add_shopping_cart',
  'actions.app_install':                             'install_mobile',
  cost_per_conversion:                               'price_change',
  // Estimated
  estimated_ad_recall_rate: 'memory',
  estimated_ad_recallers:   'lightbulb',
};

export const DEFAULT_METRICS: string[] = [
  'impressions',
  'reach',
  'clicks',
  'spend',
  'ctr',
  'cpm',
];
