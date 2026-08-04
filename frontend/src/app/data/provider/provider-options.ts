import { Provider } from './provider.enum';

export interface PlatformOption {
  value: Provider;
  label: string;
  /** Shown for visibility into the roadmap, but not selectable yet — no strategy wired up server-side */
  disabled?: boolean;
}

export const AD_PLATFORM_OPTIONS: PlatformOption[] = [
  { value: Provider.META, label: 'Meta' },
  { value: Provider.FACEBOOK, label: 'Facebook' },
  { value: Provider.INSTAGRAM, label: 'Instagram' },
  { value: Provider.TIKTOK, label: 'TikTok — coming soon', disabled: true },
  { value: Provider.GOOGLE, label: 'Google Ads — coming soon', disabled: true },
  { value: Provider.LINKEDIN, label: 'LinkedIn — coming soon', disabled: true },
  { value: Provider.X, label: 'X — coming soon', disabled: true },
];

export const META_VARIANT_LABELS: Record<string, string> = {
  ORIGINAL: 'Original',
  META_SQUARE_1080: '1:1 Square (1080×1080)',
  META_VERTICAL_1080: '4:5 Vertical (1080×1350)',
  META_STORIES_1080: '9:16 Stories (1080×1920)',
  META_LANDSCAPE_1200: '1.91:1 Landscape (1200×628)',
  META_VIDEO_SQUARE: '1:1 Square (1080×1080)',
  META_VIDEO_VERTICAL: '4:5 Vertical (1080×1350)',
  META_VIDEO_LANDSCAPE: '1.91:1 Landscape (1200×628)',
};
