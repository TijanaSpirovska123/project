import { Injectable } from '@angular/core';
import { InsightSnapshot, METRIC_CONFIG } from '../../models/insights/insight.model';

export type MetricValueFormat = 'number' | 'currency' | 'percent' | 'decimal2';

/**
 * Owns metric-value extraction, aggregation, and formatting for the Insights page —
 * the "chart math" shared by the time-series chart and the KPI cards. Pulled out of
 * InsightsComponent so this logic can be reasoned about independent of component state.
 */
@Injectable({
  providedIn: 'root',
})
export class InsightsChartDataService {
  private static readonly RATE_METRICS = new Set([
    'ctr', 'cpm', 'cpc', 'frequency',
    'unique_ctr', 'inline_link_click_ctr', 'estimated_ad_recall_rate',
  ]);

  isRateMetric(key: string): boolean {
    return InsightsChartDataService.RATE_METRICS.has(key);
  }

  formatChartDateLabel(iso: string): string {
    const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    const parts = iso.split('-');
    const m = parseInt(parts[1], 10);
    const d = parseInt(parts[2], 10);
    return (MONTHS[m - 1] ?? '') + ' ' + d;
  }

  private static formatNumber(val: number): string {
    if (val >= 1_000_000) return (val / 1_000_000).toFixed(1) + 'M';
    if (val >= 1_000) return (val / 1_000).toFixed(1) + 'K';
    return Number.isInteger(val) ? String(val) : val.toFixed(2);
  }

  /**
   * Extracts a numeric value from a rawData entry for a given metric key.
   * Handles both flat fields (e.g. "spend") and dot-notation array fields
   * (e.g. "video_play_actions.video_view", "actions.offsite_conversion.fb_pixel_purchase").
   */
  extractMetricValue(entry: any, metricKey: string): { value: number; found: boolean } {
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

  aggregateMetricRaw(metricKey: string, snapshots: InsightSnapshot[]): number {
    let total = 0;
    for (const snap of snapshots) {
      for (const entry of snap.rawData?.data ?? []) {
        const { value } = this.extractMetricValue(entry, metricKey);
        total += value;
      }
    }
    return total;
  }

  aggregateMetric(metricKey: string | null, snapshots: InsightSnapshot[]): string {
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

  formatValue(value: number, format: MetricValueFormat): string {
    switch (format) {
      case 'currency': return '$' + InsightsChartDataService.formatNumber(value);
      case 'percent': return value.toFixed(2) + '%';
      case 'decimal2': return value.toFixed(2);
      default: return InsightsChartDataService.formatNumber(value);
    }
  }

  getMetricFormat(key: string): MetricValueFormat {
    return METRIC_CONFIG.find(m => m.key === key)?.format ?? 'number';
  }
}
