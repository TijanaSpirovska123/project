export interface SegmentFilter {
  dimensionKey: string;
  segmentKey: string;
  segmentLabel: string;
  share: number;
}

export interface InsightsBreakdownRow {
  dimension: string;
  dimensionValue: string;
  spend: number;
  impressions: number;
  clicks: number;
  reach: number;
  ctr: number;
  roas: number;
  share: number;
}

export interface BreakdownDimension {
  key: string;
  label: string;
  group: string;
  metaParam: string | null;
}

export const BREAKDOWN_DIMENSIONS: BreakdownDimension[] = [
  { key: 'age',       label: 'Age',              group: 'Audience',  metaParam: 'age' },
  { key: 'gender',    label: 'Gender',           group: 'Audience',  metaParam: 'gender' },
  { key: 'country',   label: 'Country',          group: 'Audience',  metaParam: 'country' },
  { key: 'language',  label: 'Language',         group: 'Audience',  metaParam: 'age' },
  { key: 'placement', label: 'Placement',        group: 'Placement', metaParam: 'publisher_platform' },
  { key: 'device',    label: 'Device',           group: 'Placement', metaParam: 'device_platform' },
  { key: 'os',        label: 'Operating system', group: 'Placement', metaParam: 'device_platform' },
  { key: 'format',    label: 'Ad format',        group: 'Creative',  metaParam: 'publisher_platform' },
  { key: 'objective', label: 'Objective',        group: 'Campaign',  metaParam: null },
  { key: 'dow',       label: 'Day of week',      group: 'Time',      metaParam: 'hourly_stats_aggregated_by_advertiser_time_zone' },
  { key: 'hour',      label: 'Hour of day',      group: 'Time',      metaParam: 'hourly_stats_aggregated_by_advertiser_time_zone' },
];

export const BREAKDOWN_GROUPS = ['Audience', 'Placement', 'Creative', 'Campaign', 'Time'];
