import { AdSetResponse } from '../adset/adset.model';

export interface Campaign {
  id?: number;
  name: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  platform: string;
  userId?: number | null;
  adAccountId: string;
  externalId?: string;
  objective: string;
  specialAdCategories?: string;
  adSets?: AdSetResponse[];
  rawData?: Record<string, any>;
}
