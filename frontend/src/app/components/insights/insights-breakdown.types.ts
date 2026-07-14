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
  { key: 'placement', label: 'Placement',        group: 'Audience',  metaParam: 'publisher_platform' },
  { key: 'device',    label: 'Device',           group: 'Placement', metaParam: 'impression_device' },
  // Meta has no dedicated OS breakdown — value is a best-effort classification of
  // impression_device (iphone/ipad -> iOS, android_* -> Android, ...), not exact data.
  { key: 'os',        label: 'OS (approx.)',     group: 'Placement', metaParam: 'impression_device' },
  // Not a Meta insights breakdown — grouped from each snapshot's campaign objective.
  { key: 'objective', label: 'Objective',        group: 'Campaign',  metaParam: null },
  // Not a Meta insights breakdown — derived client-side from each row's date_start.
  { key: 'dow',       label: 'Day of week',      group: 'Time',      metaParam: null },
];

export const BREAKDOWN_GROUPS = ['Audience', 'Placement', 'Campaign', 'Time'];
