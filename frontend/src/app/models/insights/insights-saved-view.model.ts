export interface InsightsSavedView {
  id: number;
  name: string;
  description?: string;
  provider: 'META' | 'TIKTOK' | 'GOOGLE_ADS' | 'LINKEDIN' | 'PINTEREST' | 'REDDIT';
  viewConfig: InsightsViewConfig;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface InsightsViewConfig {
  datePreset: string | null;
  dateStart: string | null;
  dateStop: string | null;
  activePreset: number | null;
  activePresetKey: string | null;
  activeTabIndex: number;
  selectedCampaignIds: string[];
  selectedAdSetIds: string[];
  selectedAdIds: string[];
  activePlatform: string;
  compareToPrevious?: boolean;
  kpiCardMetrics?: string[];

  // Change 1 — unified tree + level selector
  treeLevel?: 'all' | 'campaigns' | 'adSets' | 'ads';
  selectedTreeRow?: { objectType: 'CAMPAIGN' | 'ADSET' | 'AD'; objectExternalId: string } | null;
  expandedNodeIds?: string[];  // prefixed: 'c:id' for campaigns, 'a:id' for adsets

  // Change 2 — visible metric chips (per-level column sets)
  visibleMetrics?: {
    campaigns: string[];
    adSets: string[];
    ads: string[];
  };

  // Change 4 — multi-platform readiness
  platformColumnVisible?: boolean;
  groupBy?: 'none' | 'platform';
  sideBySideMode?: boolean;
  selectedPlatforms?: string[];

  // Change 5 — sort
  sortBy?: { column: string; direction: 'asc' | 'desc' } | null;

  // Breakdown panels
  leftBreakdownDimension?: string;
  rightBreakdownDimension?: string;
  activeSegment?: { dimensionKey: string; segmentKey: string; segmentLabel: string; share: number } | null;
}

