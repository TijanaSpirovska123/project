import { CanonicalMetric } from '../../models/insights/ai-insight.model';

export interface AiMetricOption {
  key: CanonicalMetric;
  label: string;
}

/** Canonical, provider-independent metric vocabulary — mirrors CanonicalMetric.java. */
export const AI_METRIC_OPTIONS: AiMetricOption[] = [
  { key: 'SPEND', label: 'Spend' },
  { key: 'IMPRESSIONS', label: 'Impressions' },
  { key: 'REACH', label: 'Reach' },
  { key: 'CLICKS', label: 'Clicks' },
  { key: 'CTR', label: 'CTR' },
  { key: 'CPC', label: 'CPC' },
  { key: 'CPM', label: 'CPM' },
  { key: 'FREQUENCY', label: 'Frequency' },
  { key: 'UNIQUE_CLICKS', label: 'Unique Clicks' },
  { key: 'OUTBOUND_CLICKS', label: 'Outbound Clicks' },
  { key: 'LANDING_PAGE_VIEWS', label: 'Landing Page Views' },
  { key: 'LEADS', label: 'Leads' },
  { key: 'CONVERSIONS', label: 'Conversions' },
  { key: 'CONVERSION_RATE', label: 'Conversion Rate' },
  { key: 'PURCHASES', label: 'Purchases' },
  { key: 'PURCHASE_VALUE', label: 'Purchase Value' },
  { key: 'ROAS', label: 'ROAS' },
  { key: 'COST_PER_LEAD', label: 'Cost per Lead' },
  { key: 'COST_PER_CONVERSION', label: 'Cost per Conversion' },
  { key: 'COST_PER_PURCHASE', label: 'Cost per Purchase' },
];

export const DEFAULT_AI_METRICS: CanonicalMetric[] = ['SPEND', 'IMPRESSIONS', 'CLICKS', 'CTR', 'CPC', 'ROAS'];

export const AI_SUGGESTED_QUESTIONS: string[] = [
  'How is this performing overall?',
  'What is driving the change in spend?',
  'Where should I focus optimization efforts?',
  'Are there any risks I should be aware of?',
];
