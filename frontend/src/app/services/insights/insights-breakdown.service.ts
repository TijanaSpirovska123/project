import { Injectable } from '@angular/core';
import { InsightSnapshot } from '../../models/insights/insight.model';
import { Campaign } from '../../models/campaign/campaign';
import { InsightsBreakdownRow, SegmentFilter } from '../../components/insights/insights-breakdown.types';

export interface CustomBreakdownRow {
  dimValues: Record<string, string>;
  reach: number;
  ctr: number;
  purchases: number;
  roas: number;
  isClickable: boolean;
  isActiveFilter: boolean;
  primaryDim: string;
  primaryValue: string;
}

interface CombinedBreakdownRow {
  values: Record<string, string>;
  reach: number;
  ctr: number;
  purchases: number;
  roas: number;
}

/**
 * Owns breakdown-dimension computation for the Insights page: deriving dimension values
 * from raw Meta rows, cross-tabulating dimensions that share a breakdown source, and
 * applying active dimension filters to snapshots. Pulled out of InsightsComponent so this
 * logic (previously the source of the "placement" bug) can be reasoned about and changed
 * in one place, independent of component state.
 */
@Injectable({
  providedIn: 'root',
})
export class InsightsBreakdownService {
  /** Maps each breakdown dimension to the group key used in breakdownsJson. */
  private static readonly DIMENSION_TO_BREAKDOWN_GROUP: Record<string, string> = {
    age: 'age_gender',
    gender: 'age_gender',
    country: 'country',
    impression_device: 'placement',
    publisher_platform: 'placement',
    // "placement" is the UI-level key (see CUSTOM_TABLE_DIM_OPTIONS / BREAKDOWN_DIMENSIONS) —
    // Meta never returns a field literally named "placement", so its value is derived
    // from "publisher_platform" in readDimValue() below.
    placement: 'placement',
    // "device"/"os" reuse the same impression_device field the placement group already
    // fetches — no separate Meta call needed.
    device: 'placement',
    os: 'placement',
    // "dow" and "objective" intentionally have no group here — neither is a real Meta
    // breakdown, so they fall back (via `?? dim`) to reading the main per-day snapshot
    // rows instead of a fetched breakdown group. See readDimValue().
  };

  private static readonly WEEKDAY_NAMES = [
    'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday',
  ];

  /** Best-effort OS classification from impression_device — Meta has no OS breakdown. */
  private static classifyOs(device: string | undefined): string | undefined {
    if (!device) return undefined;
    const d = device.toLowerCase();
    if (d.includes('iphone') || d.includes('ipad') || d.includes('ipod') || d.includes('ios')) return 'iOS';
    if (d.includes('android')) return 'Android';
    if (d.includes('desktop')) return 'Desktop';
    return 'Other';
  }

  /** Day of week isn't a Meta breakdown — derived from a row's date_start. */
  private static dayOfWeekFromDate(dateStart: string | undefined): string | undefined {
    if (!dateStart) return undefined;
    const d = new Date(`${dateStart}T00:00:00Z`);
    if (Number.isNaN(d.getTime())) return undefined;
    return InsightsBreakdownService.WEEKDAY_NAMES[d.getUTCDay()];
  }

  /** Objective isn't a Meta insights breakdown — it's the snapshot's campaign's objective. */
  private getCampaignObjective(campaignExternalId: string | undefined, campaigns: Campaign[]): string | undefined {
    if (!campaignExternalId) return undefined;
    return campaigns.find(c => c.externalId === campaignExternalId)?.objective;
  }

  /** Reads a breakdown row's value for `dim`, deriving synthetic dims (placement, device, os, dow, objective). */
  private readDimValue(
    snap: InsightSnapshot,
    entry: Record<string, unknown>,
    dim: string,
    campaigns: Campaign[],
  ): string | undefined {
    switch (dim) {
      case 'placement': return entry['publisher_platform'] as string | undefined;
      case 'device':     return entry['impression_device'] as string | undefined;
      case 'os':         return InsightsBreakdownService.classifyOs(entry['impression_device'] as string | undefined);
      case 'dow':        return InsightsBreakdownService.dayOfWeekFromDate(entry['date_start'] as string | undefined);
      case 'objective':  return this.getCampaignObjective(snap.objectExternalId, campaigns);
      default:           return entry[dim] as string | undefined;
    }
  }

  private static toDouble(val: unknown): number {
    return parseFloat(String(val ?? 0)) || 0;
  }

  /** Cross-tabulates the given dimensions (which must share the same breakdown source) into combined rows. */
  computeCombinedBreakdown(
    snapshots: InsightSnapshot[],
    dimensions: string[],
    campaigns: Campaign[],
  ): CombinedBreakdownRow[] {
    const groups = new Map<string, {
      values: Record<string, string>;
      spend: number; impressions: number; clicks: number; reach: number; purchases: number; revenue: number;
    }>();
    const groupKey = InsightsBreakdownService.DIMENSION_TO_BREAKDOWN_GROUP[dimensions[0]];

    for (const snap of snapshots) {
      const structuredRows: any[] = groupKey
        ? (snap.rawData as any)?.breakdowns?.[groupKey] ?? []
        : [];
      const entriesToProcess = structuredRows.length ? structuredRows : (snap.rawData?.data ?? []);

      for (const entry of entriesToProcess) {
        const values: Record<string, string> = {};
        let missing = false;
        for (const dim of dimensions) {
          const val = this.readDimValue(snap, entry, dim, campaigns);
          if (!val) { missing = true; break; }
          values[dim] = val;
        }
        if (missing) continue;
        const key = dimensions.map(d => values[d]).join('|');
        const g = groups.get(key) ?? { values, spend: 0, impressions: 0, clicks: 0, reach: 0, purchases: 0, revenue: 0 };
        g.spend       += InsightsBreakdownService.toDouble(entry['spend']);
        g.impressions += InsightsBreakdownService.toDouble(entry['impressions']);
        g.clicks      += InsightsBreakdownService.toDouble(entry['clicks']);
        g.reach       += InsightsBreakdownService.toDouble(entry['reach']);
        const actions: any[] = Array.isArray(entry['actions']) ? entry['actions'] : [];
        const purchAct = actions.find((a: any) =>
          a.action_type === 'offsite_conversion.fb_pixel_purchase' || a.action_type === 'purchase'
        );
        g.purchases += purchAct ? InsightsBreakdownService.toDouble(purchAct.value) : 0;
        const actionValues: any[] = Array.isArray(entry['action_values']) ? entry['action_values'] : [];
        const purchVal = actionValues.find((a: any) =>
          a.action_type === 'offsite_conversion.fb_pixel_purchase' || a.action_type === 'purchase'
        );
        g.revenue += purchVal ? InsightsBreakdownService.toDouble(purchVal.value) : 0;
        groups.set(key, g);
      }
    }

    if (!groups.size) return [];
    return Array.from(groups.values())
      .sort((a, b) => b.spend - a.spend)
      .map(g => ({
        values: g.values,
        reach: g.reach,
        ctr: g.impressions > 0 ? (g.clicks / g.impressions) * 100 : 0,
        roas: g.spend > 0 ? g.revenue / g.spend : 0,
        purchases: g.purchases,
      }));
  }

  computeBreakdownWithPurchases(
    snapshots: InsightSnapshot[],
    dimension: string,
    campaigns: Campaign[],
  ): Array<{ dimensionValue: string; reach: number; ctr: number; purchases: number; roas: number }> {
    return this.computeCombinedBreakdown(snapshots, [dimension], campaigns).map(row => ({
      dimensionValue: row.values[dimension],
      reach: row.reach,
      ctr: row.ctr,
      roas: row.roas,
      purchases: row.purchases,
    }));
  }

  /**
   * Compute InsightsBreakdownRow[] client-side from the already-loaded time-series snapshots.
   * Works because FETCH_BREAKDOWNS includes age/gender/country, so each rawData entry has those
   * fields alongside date_start/spend/impressions/clicks/reach.
   */
  computeBreakdownFromInsights(
    snapshots: InsightSnapshot[],
    dimension: string,
    campaigns: Campaign[],
  ): InsightsBreakdownRow[] {
    const groups = new Map<string, { spend: number; impressions: number; clicks: number; reach: number }>();
    const groupKey = InsightsBreakdownService.DIMENSION_TO_BREAKDOWN_GROUP[dimension];

    for (const snap of snapshots) {
      // Prefer structured breakdown data merged by the backend mapper (breakdownsJson → rawData.breakdowns).
      // Fall back to rawData.data entries that happen to carry demographic fields.
      const structuredRows: any[] = groupKey
        ? (snap.rawData as any)?.breakdowns?.[groupKey] ?? []
        : [];
      const entriesToProcess = structuredRows.length ? structuredRows : (snap.rawData?.data ?? []);

      for (const entry of entriesToProcess) {
        const val = this.readDimValue(snap, entry, dimension, campaigns);
        if (!val) continue;
        const g = groups.get(val) ?? { spend: 0, impressions: 0, clicks: 0, reach: 0 };
        g.spend       += InsightsBreakdownService.toDouble(entry['spend']);
        g.impressions += InsightsBreakdownService.toDouble(entry['impressions']);
        g.clicks      += InsightsBreakdownService.toDouble(entry['clicks']);
        g.reach       += InsightsBreakdownService.toDouble(entry['reach']);
        groups.set(val, g);
      }
    }

    if (!groups.size) return [];

    const totalSpend = Array.from(groups.values()).reduce((s, g) => s + g.spend, 0);
    return Array.from(groups.entries())
      .sort((a, b) => b[1].spend - a[1].spend)
      .map(([val, g]) => ({
        dimension,
        dimensionValue: val,
        spend:       g.spend,
        impressions: g.impressions,
        clicks:      g.clicks,
        reach:       g.reach,
        ctr:         g.impressions > 0 ? (g.clicks / g.impressions) * 100 : 0,
        roas:        0,
        share:       totalSpend > 0 ? (g.spend / totalSpend) * 100 : 0,
      }));
  }

  /** Builds the Custom Breakdown table rows for the given selected dims. */
  buildCustomBreakdownTableRows(
    snapshots: InsightSnapshot[],
    dims: string[],
    campaigns: Campaign[],
    dimensionFilters: Record<string, SegmentFilter>,
  ): CustomBreakdownRow[] {
    if (dims.length === 0) return [];
    if (dims.length === 1) {
      const primaryDim = dims[0];
      const rows = this.computeBreakdownWithPurchases(snapshots, primaryDim, campaigns);
      return rows.map(row => ({
        dimValues: { [primaryDim]: row.dimensionValue },
        reach: row.reach,
        ctr: row.ctr,
        purchases: row.purchases,
        roas: row.roas,
        isClickable: true,
        isActiveFilter: dimensionFilters[primaryDim]?.segmentKey === `${primaryDim}-${row.dimensionValue}`,
        primaryDim,
        primaryValue: row.dimensionValue,
      }));
    }
    // Group selected dims by their shared breakdown source so dims that come from the
    // same source (e.g. age + gender both come from the age_gender breakdown) are
    // cross-tabulated into combined rows instead of showing '—' for each other.
    const groups = new Map<string, string[]>();
    for (const dim of dims) {
      const groupKey = InsightsBreakdownService.DIMENSION_TO_BREAKDOWN_GROUP[dim] ?? dim;
      const list = groups.get(groupKey) ?? [];
      list.push(dim);
      groups.set(groupKey, list);
    }

    const result: CustomBreakdownRow[] = [];
    const groupEntries = Array.from(groups.values());
    for (let groupIndex = 0; groupIndex < groupEntries.length; groupIndex++) {
      const groupDims = groupEntries[groupIndex];
      const rows = this.computeCombinedBreakdown(snapshots, groupDims, campaigns);
      // A secondary group (e.g. Country/Placement selected alongside Age) can't be
      // cross-tabbed with the primary rows — it's a separate, unrelated Meta breakdown
      // call with no shared row to join on. Show its top (highest-spend) value as a
      // column on every existing row instead of appending a disconnected row block.
      if (groupIndex > 0 && rows.length > 0 && result.length > 0) {
        for (const r of result) {
          for (const d of groupDims) {
            r.dimValues[d] = rows[0].values[d];
          }
        }
        continue;
      }
      if (groupDims.length > 1) {
        // Order by the primary dim's overall ranking so its values stay grouped together,
        // preserving the existing spend-desc order within each group (stable sort).
        const primaryDim = groupDims[0];
        const primaryOrder = this.computeBreakdownWithPurchases(snapshots, primaryDim, campaigns).map(r => r.dimensionValue);
        rows.sort((a, b) => primaryOrder.indexOf(a.values[primaryDim]) - primaryOrder.indexOf(b.values[primaryDim]));
      }
      let lastPrimaryValue: string | null = null;
      for (const row of rows) {
        const dimValues = dims.reduce((acc, d) => {
          acc[d] = groupDims.includes(d) ? row.values[d] : '—';
          return acc;
        }, {} as Record<string, string>);
        if (groupDims.length > 1) {
          const primaryDim = groupDims[0];
          if (dimValues[primaryDim] === lastPrimaryValue) {
            dimValues[primaryDim] = '';
          } else {
            lastPrimaryValue = dimValues[primaryDim];
          }
        }
        result.push({
          dimValues,
          reach: row.reach,
          ctr: row.ctr,
          purchases: row.purchases,
          roas: row.roas,
          isClickable: false,
          isActiveFilter: false,
          primaryDim: groupDims.join('+'),
          primaryValue: groupDims.map(d => row.values[d]).join('|'),
        });
      }
    }
    return result;
  }

  /** Filter raw data entries to only those matching every active dimension filter. */
  filterEntriesByDimensions(entries: any[], filters: SegmentFilter[]): any[] {
    if (!filters.length) return entries;
    return entries.filter(entry =>
      filters.every(f => {
        const val = f.segmentKey.slice(f.dimensionKey.length + 1);
        return entry[f.dimensionKey] === val;
      })
    );
  }

  /** Return snapshots with rawData entries pre-filtered by the active dimension filters. */
  getFilteredSnapshots(snapshots: InsightSnapshot[], filters: SegmentFilter[]): InsightSnapshot[] {
    if (!filters.length) return snapshots;
    return snapshots.map(snap => ({
      ...snap,
      rawData: snap.rawData
        ? { ...snap.rawData, data: this.filterEntriesByDimensions(snap.rawData.data ?? [], filters) }
        : snap.rawData,
    }));
  }

  /** Multiplicative proportional estimate when several dimension filters are active at once. */
  combinedSegMult(filters: SegmentFilter[]): number {
    if (!filters.length) return 1;
    return filters.reduce((acc, f) => acc * (f.share / 100), 1);
  }
}
