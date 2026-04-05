export interface StoredAssetVariantDto {
  id: number;
  variantKey: string;
  bucket: string;
  objectKey: string;
  width: number | null;
  height: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface StoredAssetDto {
  id: number;
  userId: number;
  assetType: 'IMAGE' | 'VIDEO';
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  hash: string;
  status: 'PROCESSING' | 'READY' | 'FAILED';
  createdAt: string;
  updatedAt: string;
  variants: StoredAssetVariantDto[];
}

export interface AdAssetDto {
  id?: number;
  imageHash?: string;
  url?: string;
  adAccountId?: string;
  createdAt?: string;
}

export interface CreativeDto {
  id: string | number;
  name?: string;
  status?: string;
  object_type?: string;
  thumbnail_url?: string;
  image_url?: string;
}

export interface AdSetResponse {
  id: number;
  name: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  platform: string;
  userId?: number | null;
  adAccountId: string;
  externalId: string;
  campaignId: number;
  campaignExternalId?: string;
  dailyBudget: number;
  lifetimeBudget: number;
  optimizationGoal: string;
  billingEvent: string;
  targeting?: string;
  rawData?: Record<string, any>;
}

export interface AdResponse {
  id: number;
  name: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  platform: string;
  userId?: number | null;
  adAccountId: string;
  externalId: string;
  adSetId: number;
  adSetExternalId?: string;
  creativeRefId?: string | null;
  creativeExternalId?: string | null;
  message?: string | null;
  rawData?: Record<string, any>;
}
