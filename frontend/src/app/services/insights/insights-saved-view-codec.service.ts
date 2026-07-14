import { Injectable } from '@angular/core';
import { MetricBlock, METRIC_ICONS } from '../../models/insights/insight.model';

export interface ExpandedNodeIds {
  campaignIds: Set<string>;
  adSetIds: Set<string>;
}

export interface VisibleMetricsConfig {
  campaigns: string[];
  adSets: string[];
  ads: string[];
}

/**
 * Owns the small but easy-to-get-wrong encode/decode transforms used when saving and
 * restoring an Insights "saved view": the expanded-tree-node id scheme, rebuilding KPI
 * metric blocks from persisted keys, and defaulting visible-metric columns. Pulled out of
 * InsightsComponent's buildViewConfig()/applySavedView() so the encode and decode sides
 * stay next to each other and in sync.
 */
@Injectable({
  providedIn: 'root',
})
export class InsightsSavedViewCodecService {
  /** Encodes expanded campaign/adset tree nodes into the saved view's prefixed id list. */
  encodeExpandedNodeIds(campaignIds: Set<string>, adSetIds: Set<string>): string[] {
    return [
      ...Array.from(campaignIds).map(id => `c:${id}`),
      ...Array.from(adSetIds).map(id => `a:${id}`),
    ];
  }

  /** Decodes a saved view's prefixed id list back into expanded campaign/adset tree nodes. */
  decodeExpandedNodeIds(ids: string[] | undefined): ExpandedNodeIds | null {
    if (!Array.isArray(ids)) return null;
    return {
      campaignIds: new Set(ids.filter(id => id.startsWith('c:')).map(id => id.slice(2))),
      adSetIds: new Set(ids.filter(id => id.startsWith('a:')).map(id => id.slice(2))),
    };
  }

  /** Rebuilds the 6 KPI metric blocks from a saved view's persisted metric keys, or null if not restorable. */
  buildMetricBlocksFromKeys(
    keys: (string | undefined)[] | undefined,
    formatLabel: (key: string) => string,
  ): MetricBlock[] | null {
    if (!Array.isArray(keys) || keys.length !== 6) return null;
    return keys.map((key, i) => ({
      index: i,
      metricKey: key || null,
      label: key ? formatLabel(key) : 'Click to select',
      value: '—',
      icon: key ? (METRIC_ICONS[key] ?? 'analytics') : 'add_circle',
    }));
  }

  /** Falls back to defaults per-level when a saved view is missing or has empty visible-metric lists. */
  resolveVisibleMetrics(
    saved: Partial<VisibleMetricsConfig> | undefined,
    defaults: string[],
  ): VisibleMetricsConfig {
    const pick = (list: string[] | undefined): string[] =>
      Array.isArray(list) && list.length ? list : [...defaults];
    return {
      campaigns: pick(saved?.campaigns),
      adSets: pick(saved?.adSets),
      ads: pick(saved?.ads),
    };
  }
}
