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
}

