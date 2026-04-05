export interface AnalyticsCard {
  title: string;
  total: number;
  active: number;
  paused: number;
  deleted: number;
  activePercentage: number;
  pausedPercentage: number;
  deletedPercentage: number;
}

export interface CampaignMetrics {
  impressions: number;
  reach: number;
  people: number;
  clicks: number;
  conversions: number;
  cost: number;
  ctr: number; // Click-through rate
  cpm: number; // Cost per mille
  cpc: number; // Cost per click
  impressionsChange: number;
  reachChange: number;
  peopleChange: number;
  clicksChange: number;
  conversionsChange: number;
  costChange: number;
  ctrChange: number;
  cpmChange: number;
  cpcChange: number;
}

export interface PerformanceData {
  date: string;
  impressions: number;
  reach: number;
  people: number;
  clicks: number;
  conversions: number;
  cost: number;
}

export interface DeviceBreakdown {
  name: string;
  percentage: number;
  color: string;
}

export interface AgeBreakdown {
  range: string;
  percentage: number;
  clicks: number;
}

export interface AnalyticsData {
  cards: AnalyticsCard[];
  metrics?: CampaignMetrics;
  deviceBreakdown?: DeviceBreakdown[];
  ageBreakdown?: AgeBreakdown[];
}
