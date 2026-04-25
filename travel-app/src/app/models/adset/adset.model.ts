export interface StoredAssetVariantDto {
  id: number;
  variantKey: string;
  bucket: string;
  objectKey: string;
  width: number | null;
  height: number | null;
  metaImageHash?: string | null;
  metaVideoId?: string | null;
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
  durationSeconds?: number;
  thumbnailMinioKey?: string;
  thumbnailUrl?: string;
  createdAt: string;
  updatedAt: string;
  variants: StoredAssetVariantDto[];
}

export interface StoredAssetStatusDto {
  id: number;
  status: string;
  assetType: string;
  variantCount: number;
}

export function getVariantDisplayName(variantType: string): string {
  const names: Record<string, string> = {
    'ORIGINAL':              'Original',
    'META_SQUARE_1080':      '1:1 Square (1080×1080)',
    'META_VERTICAL_1080':    '4:5 Vertical (1080×1350)',
    'META_STORIES_1080':     '9:16 Story (1080×1920)',
    'META_LANDSCAPE_1200':   '1.91:1 Landscape (1200×628)',
    'META_VIDEO_SQUARE':     '1:1 Square (1080×1080)',
    'META_VIDEO_VERTICAL':   '4:5 Vertical (1080×1350)',
    'META_VIDEO_LANDSCAPE':  '1.91:1 Landscape (1200×628)',
    'META_VIDEO_STORY':      '9:16 Story/Reels (1080×1920)',
  };
  return names[variantType] ?? variantType;
}

export interface MetaImageSyncResultDto {
  totalMetaImages: number;
  totalLocalVariants: number;
  newlyMatched: number;
  alreadyMatched: number;
  removedStale: number;
  unmatched: number;
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
