import { DatePresetId, DateRangeSelection } from '../../components/insights/adflow-date-range-picker.component';

export const CHART_COLORS = [
  '#1ca698',
  '#3498db',
  '#9b59b6',
  '#f39c12',
  '#e74c3c',
  '#2ecc71',
];

export const DEFAULT_DATE_SELECTION: DateRangeSelection = {
  preset: 'last_30d',
  dateStart: null,
  dateStop: null,
  compareToPrevious: false,
};

export const ROLLING_PRESET_DAYS: Partial<Record<DatePresetId, number>> = {
  last_7d: 7,
  last_14d: 14,
  last_30d: 30,
  last_90d: 90,
};

export const CAMPAIGN_FIELDS = [
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

export const ADSET_FIELDS = [
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

export const AD_FIELDS = [
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

export const DEFAULT_VISIBLE_METRICS = ['spend', 'impressions', 'clicks', 'ctr', 'cpm', 'reach'];

export const METRIC_GROUPS_DEF: { label: string; metrics: string[] }[] = [
  {
    label: 'Core',
    metrics: ['impressions', 'reach', 'frequency', 'clicks', 'unique_clicks',
              'spend', 'cpm', 'cpc', 'ctr', 'cpp', 'social_spend', 'cost_per_unique_click'],
  },
  {
    label: 'Conversions',
    metrics: ['conversions', 'conversion_values', 'purchase_roas', 'cost_per_conversion',
              'actions', 'unique_actions', 'action_values',
              'cost_per_action_type', 'cost_per_unique_action_type'],
  },
  {
    label: 'Video',
    metrics: ['video_play_actions', 'video_thruplay_watched_actions',
              'video_avg_time_watched_actions', 'video_p25_watched_actions',
              'video_p50_watched_actions', 'video_p75_watched_actions',
              'video_p95_watched_actions', 'video_p100_watched_actions',
              'video_30_sec_watched_actions', 'video_continuous_2_sec_watched_actions'],
  },
  {
    label: 'Engagement',
    metrics: ['inline_link_clicks', 'inline_post_engagement', 'unique_inline_link_clicks',
              'inline_link_click_ctr', 'outbound_clicks', 'unique_outbound_clicks', 'website_ctr'],
  },
];

export const PLATFORM_META: Record<string, { icon: string; label: string }> = {
  META:      { icon: 'fa-brands fa-meta',     label: 'Meta' },
  TIKTOK:    { icon: 'fa-brands fa-tiktok',   label: 'TikTok' },
  GOOGLE:    { icon: 'fa-brands fa-google',   label: 'Google' },
  LINKEDIN:  { icon: 'fa-brands fa-linkedin', label: 'LinkedIn' },
  PINTEREST: { icon: 'fa-brands fa-pinterest',label: 'Pinterest' },
  REDDIT:    { icon: 'fa-brands fa-reddit',   label: 'Reddit' },
};
